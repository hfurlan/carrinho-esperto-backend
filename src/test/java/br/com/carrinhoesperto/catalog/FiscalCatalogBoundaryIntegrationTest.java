package br.com.carrinhoesperto.catalog;

import br.com.carrinhoesperto.AbstractAuthenticatedIntegrationTest;
import br.com.carrinhoesperto.catalog.dto.ProductRequest;
import br.com.carrinhoesperto.catalog.dto.ProductResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalIssuerRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates the architectural boundary between Fiscal and Catalog (M004 §26).
 *
 * <p>A fiscal item can carry {@code rawDescription = "LEITE JUSSARA INTEGRAL 1L"}
 * while the catalog holds {@code canonicalName = "Leite UHT Integral Jussara 1L"}
 * — and neither touches the other. There is <strong>no automatic resolution</strong>
 * here: the test is about separation of concerns, not about linking the two.
 */
class FiscalCatalogBoundaryIntegrationTest extends AbstractAuthenticatedIntegrationTest {

    private static final String FISCAL_RAW_DESCRIPTION = "LEITE JUSSARA INTEGRAL 1L";
    private static final String CATALOG_CANONICAL_NAME = "Leite UHT Integral Jussara 1L";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM fiscal_document");
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private FiscalDocumentResponse importFiscalDocument() {
        FiscalItemRequest item = new FiscalItemRequest(
            null, FISCAL_RAW_DESCRIPTION, "SEM GTIN", "SEM GTIN", "697002",
            new BigDecimal("12"), "UN", new BigDecimal("5.18"), new BigDecimal("62.16"),
            null, null, null);
        FiscalDocumentRequest request = new FiscalDocumentRequest(
            "35260900371046000117650080001172161001384659", 117216L, 8,
            "2026-09-06T14:57:04-03:00",
            new FiscalIssuerRequest("00.371.046/0001-17", "SM SUPERMERCADOS MENDONCA LTDA", null, null),
            new BigDecimal("62.16"), new BigDecimal("62.16"), null, List.of(item), List.of());

        return client().post().uri("/api/fiscal-documents")
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve().body(FiscalDocumentResponse.class);
    }

    private ProductResponse createCanonicalProduct() {
        return client().post().uri("/api/catalog/products")
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ProductRequest(CATALOG_CANONICAL_NAME, null, null, null, null, null,
                null, new BigDecimal("1"), "L", "CARTON"))
            .retrieve().body(ProductResponse.class);
    }

    @Test
    void fiscalTruthAndCanonicalInterpretationCoexistWithoutEitherModifyingTheOther() {
        FiscalDocumentResponse fiscal = importFiscalDocument();
        ProductResponse product = createCanonicalProduct();

        // Distinct representations of (conceptually) the same product.
        assertThat(fiscal.items().get(0).rawDescription()).isEqualTo(FISCAL_RAW_DESCRIPTION);
        assertThat(product.canonicalName()).isEqualTo(CATALOG_CANONICAL_NAME);
        assertThat(product.canonicalName()).isNotEqualTo(fiscal.items().get(0).rawDescription());

        // Re-reading the fiscal document: raw description is untouched by catalog activity.
        FiscalDocumentResponse reread = client().get()
            .uri("/api/fiscal-documents/{id}", fiscal.id())
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().body(FiscalDocumentResponse.class);
        assertThat(reread.items().get(0).rawDescription()).isEqualTo(FISCAL_RAW_DESCRIPTION);

        // And directly in the fiscal_item row.
        String storedRaw = jdbcTemplate.queryForObject(
            "SELECT raw_description FROM fiscal_item WHERE fiscal_document_id = ?",
            String.class, fiscal.id());
        assertThat(storedRaw).isEqualTo(FISCAL_RAW_DESCRIPTION);
    }

    @Test
    void thereIsNoDatabaseLinkBetweenFiscalItemsAndCatalogProducts() {
        List<String> fiscalItemColumns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'fiscal_item'",
            String.class);
        assertThat(fiscalItemColumns)
            .doesNotContain("product_id", "catalog_product_id", "resolution_id");
    }

    @Test
    void resolutionOwnsTheLinkAndNeitherSourceContextReferencesIt() {
        // M005 added product_resolution / resolution_evidence — the link lives in
        // the Resolution context. The Fiscal <-> Catalog boundary is unchanged:
        // no FK column joins fiscal_item to product or to a resolution, and no
        // store-vocabulary tables exist yet.
        List<String> tables = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);
        assertThat(tables)
            .contains("fiscal_document", "fiscal_item", "product",
                "product_resolution", "resolution_evidence")
            .doesNotContain("store_product_code", "store_product_alias", "store_vocabulary");

        List<String> productColumns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'product'",
            String.class);
        assertThat(productColumns)
            .doesNotContain("fiscal_item_id", "resolution_id", "user_id", "owner_user_id");
    }
}
