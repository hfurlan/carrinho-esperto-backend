package br.com.carrinhoesperto.resolution;

import br.com.carrinhoesperto.AbstractAuthenticatedIntegrationTest;
import br.com.carrinhoesperto.catalog.dto.ProductRequest;
import br.com.carrinhoesperto.catalog.dto.ProductResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalIssuerRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemResponse;
import br.com.carrinhoesperto.resolution.dto.ResolutionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic Product Resolution (M005): deterministic exact-GTIN resolution of a
 * user-owned fiscal item to a shared, platform-owned catalog product — end to
 * end over real HTTP, Postgres (Flyway {@code V4__resolution.sql}) and real
 * Cognito identities. Covers the critical boundary cases (§33–§36): exact match,
 * no catalog product, no GTIN, "SEM GTIN", cEAN/cEANTrib selection, idempotency,
 * evidence, Fiscal/Catalog immutability, and cross-user ownership.
 */
class ProductResolutionIntegrationTest extends AbstractAuthenticatedIntegrationTest {

    private static final String GTIN = "7891234567895";
    private static final String OTHER_GTIN = "7899999999994";
    private static int accessKeySeq = 100;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM product_resolution");
        jdbcTemplate.update("DELETE FROM fiscal_document");
        jdbcTemplate.update("DELETE FROM product");
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private static String nextAccessKey() {
        return String.format("%044d", accessKeySeq++);
    }

    private FiscalDocumentResponse createDoc(String token, String rawDescription, String cean, String ceanTrib) {
        FiscalItemRequest item = new FiscalItemRequest(
            null, rawDescription, cean, ceanTrib, "CPROD-1",
            new BigDecimal("2"), "UN", new BigDecimal("5.18"), new BigDecimal("10.36"),
            null, null, null);
        FiscalDocumentRequest doc = new FiscalDocumentRequest(
            nextAccessKey(), 1L, 1, "2026-09-06T12:00:00-03:00",
            new FiscalIssuerRequest("00.371.046/0001-17", "SM SUPERMERCADOS MENDONCA LTDA", null, null),
            new BigDecimal("10.36"), new BigDecimal("10.36"), null, List.of(item), List.of());
        return client().post().uri("/api/fiscal-documents")
            .headers(h -> h.setBearerAuth(token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(doc)
            .retrieve().body(FiscalDocumentResponse.class);
    }

    private UUID createProduct(String canonicalName, String gtin) {
        ProductResponse product = client().post().uri("/api/catalog/products")
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ProductRequest(canonicalName, null, null, null, null, null, gtin, null, null, null))
            .retrieve().body(ProductResponse.class);
        return product.id();
    }

    private ResponseEntity<ResolutionResponse> resolve(String token, UUID fiscalItemId) {
        return client().post().uri("/api/fiscal-items/{id}/resolution", fiscalItemId)
            .headers(h -> h.setBearerAuth(token))
            .retrieve().toEntity(ResolutionResponse.class);
    }

    private int resolveStatus(String token, UUID fiscalItemId) {
        return client().post().uri("/api/fiscal-items/{id}/resolution", fiscalItemId)
            .headers(h -> {
                if (token != null) {
                    h.setBearerAuth(token);
                }
            })
            .exchange((req, res) -> res.getStatusCode().value());
    }

    // ── §34: exact GTIN match ─────────────────────────────────────────────

    @Test
    void exactGtinMatchResolvesToTheCanonicalProductWithGtinEvidenceAndLeavesFiscalUnchanged() {
        UUID productId = createProduct("Leite UHT Integral Jussara 1L", GTIN);
        FiscalDocumentResponse doc = createDoc(validAccessToken(), "LEITE JUSSARA INTEGRAL 1L", GTIN, null);
        FiscalItemResponse itemBefore = doc.items().get(0);

        ResponseEntity<ResolutionResponse> response = resolve(validAccessToken(), itemBefore.id());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResolutionResponse res = response.getBody();

        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo("RESOLVED");
        assertThat(res.method()).isEqualTo("GTIN_EXACT");
        assertThat(res.canonicalProductId()).isEqualTo(productId);
        assertThat(res.fiscalItemId()).isEqualTo(itemBefore.id());
        assertThat(res.evidence()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo("GTIN");
            assertThat(e.source()).isEqualTo("FISCAL_ITEM");
            assertThat(e.value()).isEqualTo(GTIN);
        });

        // Fiscal item unchanged, via API and directly in the row.
        FiscalItemResponse itemAfter = client().get()
            .uri("/api/fiscal-documents/{id}", doc.id())
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().body(FiscalDocumentResponse.class)
            .items().get(0);
        assertThat(itemAfter).isEqualTo(itemBefore);

        var row = jdbcTemplate.queryForMap(
            "SELECT raw_description, supplier_item_code, quantity, unit_price, total_price, gtin, gtin_taxable "
                + "FROM fiscal_item WHERE id = ?", itemBefore.id());
        assertThat(row.get("raw_description")).isEqualTo("LEITE JUSSARA INTEGRAL 1L");
        assertThat(row.get("supplier_item_code")).isEqualTo("CPROD-1");
        assertThat(row.get("gtin")).isEqualTo(GTIN);
        assertThat((BigDecimal) row.get("unit_price")).isEqualByComparingTo("5.18");
    }

    // ── §36: valid GTIN but no catalog product ────────────────────────────

    @Test
    void validGtinWithoutACatalogProductIsUnresolvedAndCreatesNoProduct() {
        FiscalDocumentResponse doc = createDoc(validAccessToken(), "LEITE JUSSARA INTEGRAL 1L", GTIN, null);

        ResolutionResponse res = resolve(validAccessToken(), doc.items().get(0).id()).getBody();

        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo("UNRESOLVED");
        assertThat(res.canonicalProductId()).isNull();
        assertThat(res.method()).isNull();
        assertThat(res.evidence()).isEmpty();

        Integer products = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM product WHERE gtin = ?", Integer.class, GTIN);
        assertThat(products).isZero();
    }

    // ── §35: no usable GTIN — must NOT fall back to description ────────────

    @Test
    void aFiscalItemWithNoGtinIsUnresolvedEvenWhenACatalogProductNameWouldMatch() {
        createProduct("Leite UHT Integral Jussara 1L", null);
        FiscalDocumentResponse doc = createDoc(validAccessToken(), "LEITE JUSSARA INTEGRAL 1L", null, null);

        ResolutionResponse res = resolve(validAccessToken(), doc.items().get(0).id()).getBody();

        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo("UNRESOLVED");
        assertThat(res.canonicalProductId()).isNull();
        assertThat(res.evidence()).isEmpty();
    }

    @Test
    void theSemGtinPlaceholderIsNotTreatedAsACanonicalGtin() {
        // A product exists, but the fiscal item only carries the "SEM GTIN" marker.
        createProduct("Leite UHT Integral Jussara 1L", GTIN);
        FiscalDocumentResponse doc = createDoc(validAccessToken(),
            "LEITE JUSSARA INTEGRAL 1L", "SEM GTIN", "SEM GTIN");

        ResolutionResponse res = resolve(validAccessToken(), doc.items().get(0).id()).getBody();

        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo("UNRESOLVED");
    }

    // ── §18: cEAN / cEANTrib selection rule ───────────────────────────────

    @Test
    void fallsBackToCeanTribOnlyWhenCeanIsNotUsable() {
        UUID productId = createProduct("Leite UHT Integral Jussara 1L", GTIN);

        // cEAN is "SEM GTIN" (not usable) -> cEANTrib is used.
        FiscalDocumentResponse withTrib = createDoc(validAccessToken(),
            "LEITE JUSSARA INTEGRAL 1L", "SEM GTIN", GTIN);
        ResolutionResponse res = resolve(validAccessToken(), withTrib.items().get(0).id()).getBody();
        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo("RESOLVED");
        assertThat(res.canonicalProductId()).isEqualTo(productId);
        assertThat(res.evidence().get(0).value()).isEqualTo(GTIN);
    }

    @Test
    void aUsableButUnmatchedCeanIsNotOverriddenByCeanTrib() {
        // Only OTHER_GTIN has a product. cEAN carries GTIN (valid format, no product);
        // cEANTrib carries OTHER_GTIN (has a product). The rule uses cEAN and stops.
        createProduct("Achocolatado Italac 400g", OTHER_GTIN);
        FiscalDocumentResponse doc = createDoc(validAccessToken(),
            "ACHOC PO ITALAC 400G", GTIN, OTHER_GTIN);

        ResolutionResponse res = resolve(validAccessToken(), doc.items().get(0).id()).getBody();

        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo("UNRESOLVED");
        assertThat(res.canonicalProductId()).isNull();
    }

    // ── §15: idempotency ─────────────────────────────────────────────────

    @Test
    void resolvingTheSameItemTwiceDoesNotCreateDuplicateRowsOrEvidence() {
        createProduct("Leite UHT Integral Jussara 1L", GTIN);
        FiscalDocumentResponse doc = createDoc(validAccessToken(), "LEITE JUSSARA INTEGRAL 1L", GTIN, null);
        UUID itemId = doc.items().get(0).id();

        ResponseEntity<ResolutionResponse> first = resolve(validAccessToken(), itemId);
        ResponseEntity<ResolutionResponse> second = resolve(validAccessToken(), itemId);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().resolutionId()).isEqualTo(first.getBody().resolutionId());
        assertThat(second.getBody().updatedAt()).isEqualTo(first.getBody().updatedAt());

        Integer resolutions = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM product_resolution WHERE fiscal_item_id = ?", Integer.class, itemId);
        assertThat(resolutions).isEqualTo(1);
        Integer evidence = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM resolution_evidence WHERE resolution_id = ?",
            Integer.class, first.getBody().resolutionId());
        assertThat(evidence).isEqualTo(1);
    }

    @Test
    void reResolvingAfterTheCatalogGainsTheProductUpdatesTheSameRowInPlace() {
        FiscalDocumentResponse doc = createDoc(validAccessToken(), "LEITE JUSSARA INTEGRAL 1L", GTIN, null);
        UUID itemId = doc.items().get(0).id();

        ResolutionResponse unresolved = resolve(validAccessToken(), itemId).getBody();
        assertThat(unresolved.status()).isEqualTo("UNRESOLVED");

        UUID productId = createProduct("Leite UHT Integral Jussara 1L", GTIN);
        ResolutionResponse resolved = resolve(validAccessToken(), itemId).getBody();

        assertThat(resolved.resolutionId()).isEqualTo(unresolved.resolutionId());
        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(resolved.canonicalProductId()).isEqualTo(productId);
        assertThat(resolved.evidence()).hasSize(1);

        Integer rows = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM product_resolution WHERE fiscal_item_id = ?", Integer.class, itemId);
        assertThat(rows).isEqualTo(1);
    }

    // ── Catalog immutability ─────────────────────────────────────────────

    @Test
    void resolutionNeverCreatesOrModifiesCatalogProducts() {
        UUID productId = createProduct("Leite UHT Integral Jussara 1L", GTIN);
        var before = jdbcTemplate.queryForMap("SELECT canonical_name, updated_at FROM product WHERE id = ?", productId);

        FiscalDocumentResponse matched = createDoc(validAccessToken(), "LEITE JUSSARA INTEGRAL 1L", GTIN, null);
        resolve(validAccessToken(), matched.items().get(0).id());
        FiscalDocumentResponse unmatched = createDoc(validAccessToken(), "OUTRO PRODUTO", OTHER_GTIN, null);
        resolve(validAccessToken(), unmatched.items().get(0).id());

        Integer productCount = jdbcTemplate.queryForObject("SELECT count(*) FROM product", Integer.class);
        assertThat(productCount).isEqualTo(1);
        var after = jdbcTemplate.queryForMap("SELECT canonical_name, updated_at FROM product WHERE id = ?", productId);
        assertThat(after).isEqualTo(before);
    }

    // ── Ownership ────────────────────────────────────────────────────────

    @Test
    void aUserCannotResolveOrReadAnotherUsersFiscalItem() {
        FiscalDocumentResponse doc = createDoc(validAccessToken(), "LEITE JUSSARA INTEGRAL 1L", GTIN, null);
        UUID itemId = doc.items().get(0).id();

        assertThat(resolveStatus(secondUserAccessToken(), itemId)).isEqualTo(404);

        int getStatus = client().get().uri("/api/fiscal-items/{id}/resolution", itemId)
            .headers(h -> h.setBearerAuth(secondUserAccessToken()))
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(getStatus).isEqualTo(404);
    }

    @Test
    void twoUsersResolveTheirOwnItemsToTheSameSharedProduct() {
        UUID productId = createProduct("Leite UHT Integral Jussara 1L", GTIN);
        FiscalDocumentResponse docA = createDoc(validAccessToken(), "LEITE JUSSARA INTEGRAL 1L", GTIN, null);
        FiscalDocumentResponse docB = createDoc(secondUserAccessToken(), "LEITE JUSSARA INTEGRAL 1L", GTIN, null);

        ResolutionResponse resA = resolve(validAccessToken(), docA.items().get(0).id()).getBody();
        ResolutionResponse resB = resolve(secondUserAccessToken(), docB.items().get(0).id()).getBody();

        assertThat(resA.status()).isEqualTo("RESOLVED");
        assertThat(resB.status()).isEqualTo("RESOLVED");
        assertThat(resA.canonicalProductId()).isEqualTo(productId);
        assertThat(resB.canonicalProductId()).isEqualTo(productId);
        assertThat(resA.resolutionId()).isNotEqualTo(resB.resolutionId());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM product", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM product_resolution", Integer.class)).isEqualTo(2);
    }

    // ── Read endpoint / errors ───────────────────────────────────────────

    @Test
    void getReturnsTheResolutionAfterItHasBeenCreated() {
        createProduct("Leite UHT Integral Jussara 1L", GTIN);
        FiscalDocumentResponse doc = createDoc(validAccessToken(), "LEITE JUSSARA INTEGRAL 1L", GTIN, null);
        UUID itemId = doc.items().get(0).id();
        UUID resolutionId = resolve(validAccessToken(), itemId).getBody().resolutionId();

        ResolutionResponse got = client().get().uri("/api/fiscal-items/{id}/resolution", itemId)
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().body(ResolutionResponse.class);
        assertThat(got.resolutionId()).isEqualTo(resolutionId);
        assertThat(got.status()).isEqualTo("RESOLVED");
    }

    @Test
    void getReturns404WhenTheItemHasNeverBeenResolved() {
        FiscalDocumentResponse doc = createDoc(validAccessToken(), "LEITE JUSSARA INTEGRAL 1L", GTIN, null);
        int status = client().get().uri("/api/fiscal-items/{id}/resolution", doc.items().get(0).id())
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(404);
    }

    @Test
    void resolvingAnUnknownFiscalItemIsNotFound() {
        assertThat(resolveStatus(validAccessToken(), UUID.randomUUID())).isEqualTo(404);
    }

    @Test
    void resolutionRequiresAuthentication() {
        FiscalDocumentResponse doc = createDoc(validAccessToken(), "LEITE JUSSARA INTEGRAL 1L", GTIN, null);
        assertThat(resolveStatus(null, doc.items().get(0).id())).isEqualTo(401);
    }

    // ── Migration / boundary ─────────────────────────────────────────────

    @Test
    void theResolutionMigrationHasBeenApplied() {
        Integer applied = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '4' AND success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void neitherFiscalItemNorProductReferencesResolution() {
        List<String> fiscalItemColumns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'fiscal_item'", String.class);
        assertThat(fiscalItemColumns).doesNotContain("product_id", "resolution_id", "canonical_product_id");

        List<String> productColumns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'product'", String.class);
        assertThat(productColumns).doesNotContain("fiscal_item_id", "resolution_id", "user_id", "owner_user_id");
    }
}
