package br.com.carrinhoesperto.admin;

import br.com.carrinhoesperto.AbstractAuthenticatedIntegrationTest;
import br.com.carrinhoesperto.admin.dto.AdminDashboardResponse;
import br.com.carrinhoesperto.admin.dto.AdminFiscalDocumentRow;
import br.com.carrinhoesperto.admin.dto.AdminFiscalItemRow;
import br.com.carrinhoesperto.admin.dto.AdminResolutionRow;
import br.com.carrinhoesperto.catalog.dto.ProductRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalIssuerRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemRequest;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin inspection surface (M006 §10, §11, §19): {@code /api/admin/**} is gated
 * server-side to ROLE_ADMIN, and its reads are platform-wide (any owner).
 */
class AdminApiIntegrationTest extends AbstractAuthenticatedIntegrationTest {

    private static int seq = 800;

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

    private int status(String path, String token) {
        return client().get().uri(path)
            .headers(h -> {
                if (token != null) {
                    h.setBearerAuth(token);
                }
            })
            .exchange((req, res) -> res.getStatusCode().value());
    }

    private <T> T get(String path, Class<T> type) {
        return client().get().uri(path)
            .headers(h -> h.setBearerAuth(adminAccessToken()))
            .retrieve().body(type);
    }

    private FiscalDocumentResponse seedDocument(String token, String gtin) {
        FiscalItemRequest item = new FiscalItemRequest(null, "LEITE TESTE 1L", gtin, null, "C1",
            new BigDecimal("1"), "UN", new BigDecimal("5.00"), new BigDecimal("5.00"), null, null, null);
        FiscalDocumentRequest doc = new FiscalDocumentRequest(
            String.format("%044d", seq++), 1L, 1, "2026-09-01T10:00:00-03:00",
            new FiscalIssuerRequest("44444444000191", "Mercado Admin", "Mercado Admin", null),
            new BigDecimal("5.00"), new BigDecimal("5.00"), null, List.of(item), List.of());
        return client().post().uri("/api/fiscal-documents")
            .headers(h -> h.setBearerAuth(token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(doc)
            .retrieve().body(FiscalDocumentResponse.class);
    }

    // ── Authorization ────────────────────────────────────────────────────

    @Test
    void nonAdminUsersAreForbiddenFromEveryAdminRoute() {
        for (String path : List.of("/api/admin/dashboard", "/api/admin/fiscal-documents",
            "/api/admin/fiscal-items", "/api/admin/resolutions", "/api/admin/catalog/products")) {
            assertThat(status(path, validAccessToken())).as(path).isEqualTo(403);
        }
    }

    @Test
    void unauthenticatedAdminRequestsAreRejected() {
        assertThat(status("/api/admin/dashboard", null)).isEqualTo(401);
    }

    @Test
    void anAdminCanReachTheAdminSurface() {
        assertThat(status("/api/admin/dashboard", adminAccessToken())).isEqualTo(200);
    }

    // ── Data access ─────────────────────────────────────────────────────

    @Test
    void adminDashboardReturnsPlatformWideCounts() {
        seedDocument(validAccessToken(), "SEM GTIN");
        seedDocument(secondUserAccessToken(), "SEM GTIN");

        AdminDashboardResponse dashboard = get("/api/admin/dashboard", AdminDashboardResponse.class);
        assertThat(dashboard.fiscalDocuments()).isGreaterThanOrEqualTo(2);
        assertThat(dashboard.fiscalItems()).isGreaterThanOrEqualTo(2);
        assertThat(dashboard.users()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void adminCanListDocumentsAndItemsAcrossOwnersAndOpenAnyDocument() {
        FiscalDocumentResponse mine = seedDocument(validAccessToken(), "SEM GTIN");
        seedDocument(secondUserAccessToken(), "SEM GTIN");

        List<AdminFiscalDocumentRow> docs = client().get().uri("/api/admin/fiscal-documents")
            .headers(h -> h.setBearerAuth(adminAccessToken()))
            .retrieve().body(new ParameterizedTypeReference<>() { });
        assertThat(docs).extracting(AdminFiscalDocumentRow::id).contains(mine.id());
        assertThat(docs).extracting(AdminFiscalDocumentRow::ownerUserId).doesNotContainNull();

        List<AdminFiscalItemRow> items = client().get().uri("/api/admin/fiscal-items")
            .headers(h -> h.setBearerAuth(adminAccessToken()))
            .retrieve().body(new ParameterizedTypeReference<>() { });
        assertThat(items).isNotEmpty();
        assertThat(items).allSatisfy(i -> assertThat(i.rawDescription()).isEqualTo("LEITE TESTE 1L"));

        // Admin can open a document that belongs to another user.
        FiscalDocumentResponse fetched = get("/api/admin/fiscal-documents/" + mine.id(), FiscalDocumentResponse.class);
        assertThat(fetched.id()).isEqualTo(mine.id());
        assertThat(fetched.items()).hasSize(1);
    }

    @Test
    void adminResolutionsViewShowsFiscalTextAndCanonicalName() {
        String gtin = "7891234567895";
        client().post().uri("/api/catalog/products")
            .headers(h -> h.setBearerAuth(adminAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ProductRequest("Leite UHT Integral Teste 1L", null, null, null, null, null,
                gtin, null, null, null))
            .retrieve().toBodilessEntity();

        FiscalDocumentResponse doc = seedDocument(validAccessToken(), gtin);
        client().post().uri("/api/fiscal-documents/{id}/resolutions", doc.id())
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().toBodilessEntity();

        List<AdminResolutionRow> rows = client().get().uri("/api/admin/resolutions")
            .headers(h -> h.setBearerAuth(adminAccessToken()))
            .retrieve().body(new ParameterizedTypeReference<>() { });

        assertThat(rows).anySatisfy(r -> {
            assertThat(r.status()).isEqualTo("RESOLVED");
            assertThat(r.fiscalRawDescription()).isEqualTo("LEITE TESTE 1L");
            assertThat(r.canonicalProductName()).isEqualTo("Leite UHT Integral Teste 1L");
            assertThat(r.evidenceCount()).isEqualTo(1);
        });
    }

    @Test
    void adminCatalogListAndSearchWork() {
        client().post().uri("/api/catalog/products")
            .headers(h -> h.setBearerAuth(adminAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ProductRequest("Refrigerante Cola 2L", null, null, null, null, null, null, null, null, null))
            .retrieve().toBodilessEntity();

        List<?> all = get("/api/admin/catalog/products", List.class);
        assertThat(all).isNotEmpty();

        List<?> search = get("/api/admin/catalog/products?name=refrigerante", List.class);
        assertThat(search).hasSize(1);
    }
}
