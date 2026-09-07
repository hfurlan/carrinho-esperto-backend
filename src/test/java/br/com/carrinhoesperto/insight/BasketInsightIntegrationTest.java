package br.com.carrinhoesperto.insight;

import br.com.carrinhoesperto.AbstractAuthenticatedIntegrationTest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.ingestion.dto.NfceImportRequest;
import br.com.carrinhoesperto.fiscal.ingestion.saopaulo.SefazSpHttpClient;
import br.com.carrinhoesperto.insight.dto.BasketInsightResponse;
import br.com.carrinhoesperto.session.dto.AnonymousSessionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Basket insight (M006.1, docs/basket-insight.md): a receipt is grouped into
 * categories with a health seal, derived on read from fiscal truth, and scoped
 * to the caller exactly like the receipt itself.
 */
class BasketInsightIntegrationTest extends AbstractAuthenticatedIntegrationTest {

    private static final String ACCESS_KEY = "35260900371046000117650080001172161001384659";
    private static final String QR_URL =
        "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx"
            + "?p=" + ACCESS_KEY + "%7C2%7C1%7C1%7C16AA3D2FF0C03038550613411FF3B328615A3F80";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SefazSpHttpClient sefazSpHttpClient;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM fiscal_document");
        jdbcTemplate.update("DELETE FROM app_user WHERE kind = 'ANONYMOUS'");
        byte[] fixture;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/nfce/sao-paulo-consulta-qrcode.html")) {
            fixture = in.readAllBytes();
        }
        given(sefazSpHttpClient.fetch(any())).willReturn(fixture);
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private String newAnonymousToken() {
        return client().post().uri("/api/anonymous-sessions")
            .retrieve().body(AnonymousSessionResponse.class).token();
    }

    @Test
    void groupsAReceiptIntoCategoriesWithASeal() {
        String token = newAnonymousToken();
        FiscalDocumentResponse doc = client().post().uri("/api/fiscal-documents/import")
            .header("X-Anon-Session", token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new NfceImportRequest(QR_URL))
            .retrieve().body(FiscalDocumentResponse.class);
        assertThat(doc).isNotNull();

        BasketInsightResponse insight = client().get()
            .uri("/api/fiscal-documents/{id}/insight", doc.id())
            .header("X-Anon-Session", token)
            .retrieve().body(BasketInsightResponse.class);

        assertThat(insight).isNotNull();
        assertThat(insight.documentId()).isEqualTo(doc.id());
        assertThat(insight.seal()).isIn("EQUILIBRADO", "ATENCAO", "MUITOS_SUPERFLUOS", "SEM_DADOS");
        assertThat(insight.sealLabel()).isNotBlank();
        assertThat(insight.messages()).isNotEmpty();
        assertThat(insight.categories()).isNotEmpty();

        // Shares are fractions in [0,1] and the category shares add up to ~1.
        assertThat(insight.superfluousShare()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        assertThat(insight.basicShare()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        BigDecimal sum = insight.categories().stream()
            .map(c -> c.valueShare())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isBetween(new BigDecimal("0.98"), new BigDecimal("1.02"));

        // Categories are ordered by value, biggest first.
        List<BigDecimal> values = insight.categories().stream().map(c -> c.totalValue()).toList();
        assertThat(values).isSortedAccordingTo((a, b) -> b.compareTo(a));
    }

    @Test
    void insightIsScopedToTheOwner() {
        String owner = newAnonymousToken();
        FiscalDocumentResponse doc = client().post().uri("/api/fiscal-documents/import")
            .header("X-Anon-Session", owner)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new NfceImportRequest(QR_URL))
            .retrieve().body(FiscalDocumentResponse.class);

        String stranger = newAnonymousToken();
        int status = client().get().uri("/api/fiscal-documents/{id}/insight", doc.id())
            .header("X-Anon-Session", stranger)
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(404);
    }
}
