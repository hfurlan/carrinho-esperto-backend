package br.com.carrinhoesperto.resolution;

import br.com.carrinhoesperto.AbstractAuthenticatedIntegrationTest;
import br.com.carrinhoesperto.catalog.dto.ProductRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalIssuerRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemRequest;
import br.com.carrinhoesperto.resolution.dto.DocumentItemResolutionResponse;
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
 * Document-level resolution (M006): resolve / read the resolution state of every
 * item of a fiscal document in one call, for the receipt-detail screen.
 */
class DocumentResolutionIntegrationTest extends AbstractAuthenticatedIntegrationTest {

    private static final String GTIN = "7891234567895";
    private static int seq = 900;

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

    private FiscalDocumentResponse importDoc(String token) {
        FiscalItemRequest withGtin = new FiscalItemRequest(null, "LEITE JUSSARA INTEGRAL 1L", GTIN, null, "111",
            new BigDecimal("1"), "UN", new BigDecimal("5.18"), new BigDecimal("5.18"), null, null, null);
        FiscalItemRequest noGtin = new FiscalItemRequest(null, "PAO FRANCES KG", "SEM GTIN", "SEM GTIN", "222",
            new BigDecimal("0.5"), "KG", new BigDecimal("12.00"), new BigDecimal("6.00"), null, null, null);
        FiscalDocumentRequest doc = new FiscalDocumentRequest(
            String.format("%044d", seq++), 1L, 1, "2026-09-01T10:00:00-03:00",
            new FiscalIssuerRequest("55555555000191", "Mercado", null, null),
            new BigDecimal("11.18"), new BigDecimal("11.18"), null, List.of(withGtin, noGtin), List.of());
        return client().post().uri("/api/fiscal-documents")
            .headers(h -> h.setBearerAuth(token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(doc)
            .retrieve().body(FiscalDocumentResponse.class);
    }

    private void createProduct(String canonicalName, String gtin) {
        client().post().uri("/api/catalog/products")
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ProductRequest(canonicalName, null, null, null, null, null, gtin, null, null, null))
            .retrieve().toBodilessEntity();
    }

    private List<DocumentItemResolutionResponse> post(String token, UUID documentId) {
        return client().post().uri("/api/fiscal-documents/{id}/resolutions", documentId)
            .headers(h -> h.setBearerAuth(token))
            .retrieve().body(new ParameterizedTypeReference<>() { });
    }

    @Test
    void batchResolveReturnsPerItemStateAndEnrichesTheCanonicalName() {
        createProduct("Leite UHT Integral Jussara 1L", GTIN);
        FiscalDocumentResponse doc = importDoc(validAccessToken());

        List<DocumentItemResolutionResponse> result = post(validAccessToken(), doc.id());

        assertThat(result).hasSize(2);
        DocumentItemResolutionResponse resolved = result.stream()
            .filter(r -> "RESOLVED".equals(r.status())).findFirst().orElseThrow();
        assertThat(resolved.canonicalProductName()).isEqualTo("Leite UHT Integral Jussara 1L");
        assertThat(resolved.method()).isEqualTo("GTIN_EXACT");
        assertThat(result).anySatisfy(r -> assertThat(r.status()).isEqualTo("UNRESOLVED"));
    }

    @Test
    void readEndpointReportsNullStatusBeforeAnyResolution() {
        FiscalDocumentResponse doc = importDoc(validAccessToken());

        List<DocumentItemResolutionResponse> before = client().get()
            .uri("/api/fiscal-documents/{id}/resolutions", doc.id())
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().body(new ParameterizedTypeReference<>() { });
        assertThat(before).hasSize(2);
        assertThat(before).allSatisfy(r -> assertThat(r.status()).isNull());

        post(validAccessToken(), doc.id());

        List<DocumentItemResolutionResponse> after = client().get()
            .uri("/api/fiscal-documents/{id}/resolutions", doc.id())
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().body(new ParameterizedTypeReference<>() { });
        assertThat(after).noneSatisfy(r -> assertThat(r.status()).isNull());
    }

    @Test
    void batchResolveIsIdempotent() {
        createProduct("Leite UHT Integral Jussara 1L", GTIN);
        FiscalDocumentResponse doc = importDoc(validAccessToken());

        post(validAccessToken(), doc.id());
        post(validAccessToken(), doc.id());

        Integer rows = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM product_resolution r JOIN fiscal_item i ON i.id = r.fiscal_item_id "
                + "WHERE i.fiscal_document_id = ?", Integer.class, doc.id());
        assertThat(rows).isEqualTo(2);
    }

    @Test
    void anotherUserCannotResolveOrReadThisDocument() {
        FiscalDocumentResponse doc = importDoc(validAccessToken());

        int postStatus = client().post().uri("/api/fiscal-documents/{id}/resolutions", doc.id())
            .headers(h -> h.setBearerAuth(secondUserAccessToken()))
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(postStatus).isEqualTo(404);

        int getStatus = client().get().uri("/api/fiscal-documents/{id}/resolutions", doc.id())
            .headers(h -> h.setBearerAuth(secondUserAccessToken()))
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(getStatus).isEqualTo(404);
    }
}
