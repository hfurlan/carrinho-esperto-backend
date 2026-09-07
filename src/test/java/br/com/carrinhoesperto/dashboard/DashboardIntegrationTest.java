package br.com.carrinhoesperto.dashboard;

import br.com.carrinhoesperto.AbstractAuthenticatedIntegrationTest;
import br.com.carrinhoesperto.dashboard.dto.DashboardSpendingResponse;
import br.com.carrinhoesperto.dashboard.dto.DashboardSummaryResponse;
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
 * Consumer dashboard aggregation (M006 §4, §8, §19): headline summary and
 * spending-by-month / by-supermarket, strictly scoped to the authenticated
 * user's own imported receipts.
 */
class DashboardIntegrationTest extends AbstractAuthenticatedIntegrationTest {

    private static int seq = 700;

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

    private FiscalDocumentResponse importDoc(String token, String issuerCnpj, String issuedAt,
                                             String total, int items) {
        FiscalItemRequest[] itemArr = new FiscalItemRequest[items];
        for (int i = 0; i < items; i++) {
            itemArr[i] = new FiscalItemRequest(null, "ITEM " + i, null, null, "C" + i,
                new BigDecimal("1"), "UN", new BigDecimal("1.00"), new BigDecimal("1.00"), null, null, null);
        }
        FiscalDocumentRequest doc = new FiscalDocumentRequest(
            String.format("%044d", seq++), 1L, 1, issuedAt,
            new FiscalIssuerRequest(issuerCnpj, "Mercado " + issuerCnpj, "Mercado " + issuerCnpj, null),
            new BigDecimal(total), new BigDecimal(total), null, List.of(itemArr), List.of());
        return client().post().uri("/api/fiscal-documents")
            .headers(h -> h.setBearerAuth(token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(doc)
            .retrieve().body(FiscalDocumentResponse.class);
    }

    private DashboardSummaryResponse summary(String token) {
        return client().get().uri("/api/dashboard/summary")
            .headers(h -> h.setBearerAuth(token))
            .retrieve().body(DashboardSummaryResponse.class);
    }

    private DashboardSpendingResponse spending(String token) {
        return client().get().uri("/api/dashboard/spending")
            .headers(h -> h.setBearerAuth(token))
            .retrieve().body(DashboardSpendingResponse.class);
    }

    @Test
    void summaryCountsOnlyTheCallersOwnReceipts() {
        importDoc(validAccessToken(), "11111111000191", "2026-07-10T10:00:00-03:00", "40.00", 3);
        importDoc(validAccessToken(), "22222222000191", "2026-08-10T10:00:00-03:00", "60.00", 2);
        importDoc(secondUserAccessToken(), "11111111000191", "2026-08-11T10:00:00-03:00", "999.00", 5);

        DashboardSummaryResponse mine = summary(validAccessToken());
        assertThat(mine.receiptCount()).isEqualTo(2);
        assertThat(mine.itemCount()).isEqualTo(5);
        assertThat(mine.totalSpent()).isEqualByComparingTo("100.00");
        assertThat(mine.resolvedItemCount()).isZero();
        assertThat(mine.unresolvedItemCount()).isEqualTo(5);

        DashboardSummaryResponse other = summary(secondUserAccessToken());
        assertThat(other.receiptCount()).isEqualTo(1);
        assertThat(other.totalSpent()).isEqualByComparingTo("999.00");
    }

    @Test
    void summaryReflectsResolvedItemsAfterResolution() {
        String gtin = "7891234567895";
        client().post().uri("/api/catalog/products")
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new br.com.carrinhoesperto.catalog.dto.ProductRequest(
                "Produto Teste", null, null, null, null, null, gtin, null, null, null))
            .retrieve().toBodilessEntity();

        FiscalItemRequest item = new FiscalItemRequest(null, "PRODUTO X", gtin, null, "C1",
            new BigDecimal("1"), "UN", new BigDecimal("5.00"), new BigDecimal("5.00"), null, null, null);
        FiscalDocumentRequest doc = new FiscalDocumentRequest(
            String.format("%044d", seq++), 1L, 1, "2026-09-01T10:00:00-03:00",
            new FiscalIssuerRequest("33333333000191", "Mercado", null, null),
            new BigDecimal("5.00"), new BigDecimal("5.00"), null, List.of(item), List.of());
        FiscalDocumentResponse created = client().post().uri("/api/fiscal-documents")
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .contentType(MediaType.APPLICATION_JSON).body(doc)
            .retrieve().body(FiscalDocumentResponse.class);

        assertThat(summary(validAccessToken()).resolvedItemCount()).isZero();

        client().post().uri("/api/fiscal-documents/{id}/resolutions", created.id())
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().toBodilessEntity();

        assertThat(summary(validAccessToken()).resolvedItemCount()).isEqualTo(1);
    }

    @Test
    void spendingIsGroupedByMonthOldestFirst() {
        importDoc(validAccessToken(), "11111111000191", "2026-07-10T10:00:00-03:00", "40.00", 1);
        importDoc(validAccessToken(), "11111111000191", "2026-07-20T10:00:00-03:00", "10.00", 1);
        importDoc(validAccessToken(), "11111111000191", "2026-09-05T10:00:00-03:00", "30.00", 1);

        DashboardSpendingResponse spending = spending(validAccessToken());
        assertThat(spending.byMonth()).extracting("month").containsExactly("2026-07", "2026-09");
        assertThat(spending.byMonth().get(0).total()).isEqualByComparingTo("50.00");
        assertThat(spending.byMonth().get(0).receiptCount()).isEqualTo(2);
    }

    @Test
    void spendingIsGroupedByIssuerBiggestFirst() {
        importDoc(validAccessToken(), "11111111000191", "2026-09-01T10:00:00-03:00", "20.00", 1);
        importDoc(validAccessToken(), "22222222000191", "2026-09-02T10:00:00-03:00", "90.00", 1);

        DashboardSpendingResponse spending = spending(validAccessToken());
        assertThat(spending.byIssuer()).hasSize(2);
        assertThat(spending.byIssuer().get(0).issuerCnpj()).isEqualTo("22222222000191");
        assertThat(spending.byIssuer().get(0).total()).isEqualByComparingTo("90.00");
    }

    @Test
    void aNewUserSeesZeroedSummaryAndEmptySpending() {
        DashboardSummaryResponse summary = summary(secondUserAccessToken());
        assertThat(summary.receiptCount()).isZero();
        assertThat(summary.totalSpent()).isEqualByComparingTo("0");
        assertThat(spending(secondUserAccessToken()).byMonth()).isEmpty();
    }

    @Test
    void dashboardRequiresAuthentication() {
        int status = client().get().uri("/api/dashboard/summary")
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(401);
    }
}
