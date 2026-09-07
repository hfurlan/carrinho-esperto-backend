package br.com.carrinhoesperto.catalog;

import br.com.carrinhoesperto.AbstractAuthenticatedIntegrationTest;
import br.com.carrinhoesperto.catalog.dto.BrandRequest;
import br.com.carrinhoesperto.catalog.dto.BrandResponse;
import br.com.carrinhoesperto.catalog.dto.CategoryRequest;
import br.com.carrinhoesperto.catalog.dto.CategoryResponse;
import br.com.carrinhoesperto.catalog.dto.ManufacturerRequest;
import br.com.carrinhoesperto.catalog.dto.ManufacturerResponse;
import br.com.carrinhoesperto.catalog.dto.ProductFamilyRequest;
import br.com.carrinhoesperto.catalog.dto.ProductFamilyResponse;
import br.com.carrinhoesperto.catalog.dto.ProductRequest;
import br.com.carrinhoesperto.catalog.dto.ProductResponse;
import br.com.carrinhoesperto.catalog.dto.SubcategoryRequest;
import br.com.carrinhoesperto.catalog.dto.SubcategoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catalog Foundation (M004): create/retrieve every canonical entity, GTIN
 * uniqueness + lookup, id lookup, deterministic name search, foreign-key and
 * package validation, and the platform-owned (not user-owned) ownership model.
 * End to end over real HTTP, Postgres (Flyway {@code V3__catalog.sql}) and a
 * real Cognito identity.
 */
class CatalogIntegrationTest extends AbstractAuthenticatedIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_family");
        jdbcTemplate.update("DELETE FROM subcategory");
        jdbcTemplate.update("DELETE FROM category");
        jdbcTemplate.update("DELETE FROM brand");
        jdbcTemplate.update("DELETE FROM manufacturer");
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        return client().post().uri(path)
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().body(type);
    }

    private int postStatus(String path, Object body) {
        return client().post().uri(path)
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange((req, res) -> res.getStatusCode().value());
    }

    private int getStatus(String path, String token) {
        return client().get().uri(path)
            .headers(h -> h.setBearerAuth(token))
            .exchange((req, res) -> res.getStatusCode().value());
    }

    private ManufacturerResponse newManufacturer(String name) {
        return post("/api/catalog/manufacturers", new ManufacturerRequest(name), ManufacturerResponse.class);
    }

    private BrandResponse newBrand(String name, UUID manufacturerId) {
        return post("/api/catalog/brands", new BrandRequest(name, manufacturerId), BrandResponse.class);
    }

    private CategoryResponse newCategory(String name) {
        return post("/api/catalog/categories", new CategoryRequest(name), CategoryResponse.class);
    }

    private SubcategoryResponse newSubcategory(UUID categoryId, String name) {
        return post("/api/catalog/subcategories", new SubcategoryRequest(categoryId, name), SubcategoryResponse.class);
    }

    private ProductResponse newProduct(ProductRequest request) {
        return post("/api/catalog/products", request, ProductResponse.class);
    }

    private ProductRequest bareProduct(String canonicalName) {
        return new ProductRequest(canonicalName, null, null, null, null, null, null, null, null, null);
    }

    // ── Manufacturer ───────────────────────────────────────────────────────

    @Test
    void createsAndRetrievesAManufacturer() {
        ManufacturerResponse created = newManufacturer("Jussara Alimentos");
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Jussara Alimentos");

        ManufacturerResponse fetched = client().get()
            .uri("/api/catalog/manufacturers/{id}", created.id())
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().body(ManufacturerResponse.class);
        assertThat(fetched.id()).isEqualTo(created.id());
    }

    @Test
    void rejectsABlankManufacturerName() {
        assertThat(postStatus("/api/catalog/manufacturers", new ManufacturerRequest("   "))).isEqualTo(400);
    }

    @Test
    void rejectsADuplicateManufacturerName() {
        newManufacturer("BRF");
        assertThat(postStatus("/api/catalog/manufacturers", new ManufacturerRequest("BRF"))).isEqualTo(400);
    }

    // ── Brand ──────────────────────────────────────────────────────────────

    @Test
    void createsABrandWithAndWithoutAManufacturer() {
        ManufacturerResponse brf = newManufacturer("BRF");
        BrandResponse perdigao = newBrand("Perdigão", brf.id());
        assertThat(perdigao.manufacturerId()).isEqualTo(brf.id());

        BrandResponse carrefour = newBrand("Carrefour", null);
        assertThat(carrefour.manufacturerId()).isNull();
    }

    @Test
    void rejectsABrandWithAnUnknownManufacturer() {
        assertThat(postStatus("/api/catalog/brands",
            new BrandRequest("Ghost", UUID.randomUUID()))).isEqualTo(404);
    }

    // ── Category / Subcategory ─────────────────────────────────────────────

    @Test
    void createsACategoryAndASubcategoryLinkedToIt() {
        CategoryResponse laticinios = newCategory("Laticínios");
        SubcategoryResponse leites = newSubcategory(laticinios.id(), "Leites");
        assertThat(leites.categoryId()).isEqualTo(laticinios.id());
    }

    @Test
    void rejectsASubcategoryWithAnUnknownCategory() {
        assertThat(postStatus("/api/catalog/subcategories",
            new SubcategoryRequest(UUID.randomUUID(), "Leites"))).isEqualTo(404);
    }

    @Test
    void rejectsASubcategoryWithoutACategory() {
        assertThat(postStatus("/api/catalog/subcategories",
            new SubcategoryRequest(null, "Leites"))).isEqualTo(400);
    }

    // ── Product family ─────────────────────────────────────────────────────

    @Test
    void createsAProductFamilyWithAndWithoutABrand() {
        BrandResponse jussara = newBrand("Jussara", null);
        ProductFamilyResponse withBrand = post("/api/catalog/product-families",
            new ProductFamilyRequest(jussara.id(), "Leite UHT Integral"), ProductFamilyResponse.class);
        assertThat(withBrand.brandId()).isEqualTo(jussara.id());

        ProductFamilyResponse noBrand = post("/api/catalog/product-families",
            new ProductFamilyRequest(null, "Leite UHT Integral"), ProductFamilyResponse.class);
        assertThat(noBrand.brandId()).isNull();
    }

    // ── Product ────────────────────────────────────────────────────────────

    @Test
    void createsAProductWithAllRelationships() {
        ManufacturerResponse manufacturer = newManufacturer("Jussara Alimentos");
        BrandResponse brand = newBrand("Jussara", manufacturer.id());
        CategoryResponse category = newCategory("Laticínios");
        SubcategoryResponse subcategory = newSubcategory(category.id(), "Leites");
        ProductFamilyResponse family = post("/api/catalog/product-families",
            new ProductFamilyRequest(brand.id(), "Leite UHT Integral"), ProductFamilyResponse.class);

        ProductResponse product = newProduct(new ProductRequest(
            "Leite UHT Integral Jussara 1L", brand.id(), manufacturer.id(), category.id(),
            subcategory.id(), family.id(), "7891234567895", new BigDecimal("1"), "l", "carton"));

        assertThat(product.id()).isNotNull();
        assertThat(product.brandId()).isEqualTo(brand.id());
        assertThat(product.manufacturerId()).isEqualTo(manufacturer.id());
        assertThat(product.categoryId()).isEqualTo(category.id());
        assertThat(product.subcategoryId()).isEqualTo(subcategory.id());
        assertThat(product.productFamilyId()).isEqualTo(family.id());
        assertThat(product.gtin()).isEqualTo("7891234567895");
        assertThat(product.packageQuantity()).isEqualByComparingTo("1");
        assertThat(product.packageUnit()).isEqualTo("L");
        assertThat(product.packagingType()).isEqualTo("CARTON");
    }

    @Test
    void createsAProductWithOnlyACanonicalName() {
        ProductResponse product = newProduct(bareProduct("Salsicha Hot Dog Perdigão"));
        assertThat(product.brandId()).isNull();
        assertThat(product.manufacturerId()).isNull();
        assertThat(product.gtin()).isNull();
        assertThat(product.packageQuantity()).isNull();
        assertThat(product.packageUnit()).isNull();
        assertThat(product.packagingType()).isNull();
    }

    @Test
    void rejectsAProductWithAnUnknownForeignKey() {
        ProductRequest request = new ProductRequest(
            "Ghost", UUID.randomUUID(), null, null, null, null, null, null, null, null);
        assertThat(postStatus("/api/catalog/products", request)).isEqualTo(404);
    }

    @Test
    void enforcesCanonicalGtinUniqueness() {
        newProduct(new ProductRequest("Leite A", null, null, null, null, null,
            "7891234567895", null, null, null));

        int status = postStatus("/api/catalog/products", new ProductRequest(
            "Leite B", null, null, null, null, null, "7891234567895", null, null, null));
        assertThat(status).isEqualTo(409);
    }

    @Test
    void rejectsTheSemGtinPlaceholderAsACanonicalGtin() {
        assertThat(postStatus("/api/catalog/products", new ProductRequest(
            "Leite", null, null, null, null, null, "SEM GTIN", null, null, null))).isEqualTo(400);
    }

    @Test
    void rejectsAMalformedGtin() {
        assertThat(postStatus("/api/catalog/products", new ProductRequest(
            "Leite", null, null, null, null, null, "12ab", null, null, null))).isEqualTo(400);
    }

    @Test
    void rejectsAnInvalidPackageUnit() {
        assertThat(postStatus("/api/catalog/products", new ProductRequest(
            "Leite", null, null, null, null, null, null, new BigDecimal("1"), "LITRO", null))).isEqualTo(400);
    }

    @Test
    void rejectsANonPositivePackageQuantity() {
        assertThat(postStatus("/api/catalog/products", new ProductRequest(
            "Leite", null, null, null, null, null, null, BigDecimal.ZERO, "L", null))).isEqualTo(400);
    }

    @Test
    void looksUpAProductById() {
        ProductResponse created = newProduct(bareProduct("Leite UHT Integral Jussara 1L"));

        ProductResponse fetched = client().get()
            .uri("/api/catalog/products/{id}", created.id())
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().body(ProductResponse.class);
        assertThat(fetched.id()).isEqualTo(created.id());

        assertThat(getStatus("/api/catalog/products/" + UUID.randomUUID(), validAccessToken())).isEqualTo(404);
    }

    @Test
    void looksUpAProductByCanonicalGtin() {
        ProductResponse created = newProduct(new ProductRequest(
            "Leite UHT Integral Jussara 1L", null, null, null, null, null,
            "7891234567895", null, null, null));

        List<ProductResponse> byGtin = client().get()
            .uri("/api/catalog/products?gtin=7891234567895")
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().body(new ParameterizedTypeReference<>() { });
        assertThat(byGtin).extracting(ProductResponse::id).containsExactly(created.id());

        List<ProductResponse> unknown = client().get()
            .uri("/api/catalog/products?gtin=0000000000000")
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().body(new ParameterizedTypeReference<>() { });
        assertThat(unknown).isEmpty();
    }

    @Test
    void searchesProductsByCanonicalNameDeterministicallyAndCaseInsensitively() {
        newProduct(bareProduct("Leite UHT Integral Jussara 1L"));
        newProduct(bareProduct("Leite Condensado Italac 395g"));
        newProduct(bareProduct("Salsicha Hot Dog Perdigão"));

        List<ProductResponse> leite = search("leite");
        assertThat(leite).extracting(ProductResponse::canonicalName)
            .containsExactly("Leite Condensado Italac 395g", "Leite UHT Integral Jussara 1L");

        assertThat(search("JUSSARA")).hasSize(1);
        assertThat(search("xyz")).isEmpty();
    }

    private List<ProductResponse> search(String name) {
        return client().get()
            .uri("/api/catalog/products?name={name}", name)
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().body(new ParameterizedTypeReference<>() { });
    }

    @Test
    void storesCanonicalNamesVerbatimWithoutLowercasingOrDeaccenting() {
        ProductResponse product = newProduct(bareProduct("Leite UHT Integral Jussara 1L"));
        assertThat(product.canonicalName()).isEqualTo("Leite UHT Integral Jussara 1L");

        BrandResponse brand = newBrand("Perdigão", null);
        assertThat(brand.name()).isEqualTo("Perdigão");
    }

    // ── Ownership: platform-owned, not user-owned ──────────────────────────

    @Test
    void catalogProductsAreNotUserOwned() {
        List<String> columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'product'",
            String.class);
        assertThat(columns).doesNotContain("user_id", "owner_user_id", "owner_id");
    }

    @Test
    void aProductCreatedByOneUserIsVisibleToAnother() {
        ProductResponse mine = newProduct(bareProduct("Leite UHT Integral Jussara 1L"));

        ProductResponse seenByOther = client().get()
            .uri("/api/catalog/products/{id}", mine.id())
            .headers(h -> h.setBearerAuth(secondUserAccessToken()))
            .retrieve().body(ProductResponse.class);

        assertThat(seenByOther.id()).isEqualTo(mine.id());
        assertThat(seenByOther.canonicalName()).isEqualTo(mine.canonicalName());
    }

    // ── Migration ─────────────────────────────────────────────────────────

    @Test
    void theCatalogMigrationHasBeenApplied() {
        Integer applied = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '3' AND success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void requiresAuthentication() {
        int status = client().get().uri("/api/catalog/products/" + UUID.randomUUID())
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(401);
    }
}
