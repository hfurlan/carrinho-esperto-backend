package br.com.carrinhoesperto.fiscal.ingestion;

import br.com.carrinhoesperto.AbstractAuthenticatedIntegrationTest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalPaymentResponse;
import br.com.carrinhoesperto.fiscal.ingestion.dto.NfceImportRequest;
import br.com.carrinhoesperto.fiscal.ingestion.saopaulo.SefazSpHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * End-to-end import (M003 §20): {@code POST /api/fiscal-documents/import} with a
 * São Paulo QR URL → provider → parser → existing M002 Fiscal service →
 * Postgres. The outbound SEFAZ call is replaced by a mock serving the sanitized
 * fixture, so the test never touches the live site.
 */
class NfceImportIntegrationTest extends AbstractAuthenticatedIntegrationTest {

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
        byte[] fixture;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/nfce/sao-paulo-consulta-qrcode.html")) {
            fixture = in.readAllBytes();
        }
        given(sefazSpHttpClient.fetch(any())).willReturn(fixture);
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private ResponseEntity<FiscalDocumentResponse> importAs(String token, String qrCodeUrl) {
        return client().post().uri("/api/fiscal-documents/import")
            .headers(h -> h.setBearerAuth(token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new NfceImportRequest(qrCodeUrl))
            .retrieve()
            .toEntity(FiscalDocumentResponse.class);
    }

    private int importStatus(String token, String qrCodeUrl) {
        return client().post().uri("/api/fiscal-documents/import")
            .headers(h -> h.setBearerAuth(token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new NfceImportRequest(qrCodeUrl))
            .exchange((req, res) -> res.getStatusCode().value());
    }

    @Test
    void importsParsesAndPersistsTheConsultation() {
        ResponseEntity<FiscalDocumentResponse> response = importAs(validAccessToken(), QR_URL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        FiscalDocumentResponse doc = response.getBody();
        assertThat(doc).isNotNull();
        assertThat(doc.accessKey()).isEqualTo(ACCESS_KEY);
        assertThat(doc.documentNumber()).isEqualTo(117216L);
        assertThat(doc.series()).isEqualTo(8);
        assertThat(doc.issuer().cnpj()).isEqualTo("00371046000117");
        assertThat(doc.issuer().legalName()).isEqualTo("SM SUPERMERCADOS MENDONCA LTDA");
        assertThat(doc.totalItemsAmount()).isEqualByComparingTo("328.11");
        assertThat(doc.totalDiscount()).isEqualByComparingTo("12.88");
        assertThat(doc.totalAmount()).isEqualByComparingTo("315.23");
        assertThat(doc.items()).hasSize(22);
        assertThat(doc.payments()).hasSize(2);

        // Persisted and retrievable by the owner.
        FiscalDocumentResponse fetched = client().get()
            .uri("/api/fiscal-documents/{id}", doc.id())
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().body(FiscalDocumentResponse.class);
        assertThat(fetched).isNotNull();
        assertThat(fetched.items()).hasSize(22);

        Integer rows = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM fiscal_document WHERE access_key = ? AND owner_user_id IN "
                + "(SELECT id FROM app_user WHERE cognito_subject = ?)",
            Integer.class, ACCESS_KEY, expectedSubject());
        assertThat(rows).isEqualTo(1);

        // Consumer data is never parsed or stored: the issuer address stops at the
        // address line and does not slurp the (redacted) consumer block.
        String storedAddress = jdbcTemplate.queryForObject(
            "SELECT issuer_address FROM fiscal_document WHERE access_key = ?", String.class, ACCESS_KEY);
        assertThat(storedAddress).doesNotContain("CPF").doesNotContain("000.000.000-00").doesNotContain("CONSUMIDOR");
    }

    @Test
    void preservesRawDescriptionsCprodWeightedQuantitiesAndPayments() {
        FiscalDocumentResponse doc = importAs(validAccessToken(), QR_URL).getBody();
        assertThat(doc).isNotNull();

        assertThat(doc.items()).extracting(FiscalItemResponse::rawDescription)
            .contains("SALSICHA HOT DOG PERDIGAO", "LEITE JUSSARA INTEGRAL 1L");
        assertThat(doc.items())
            .filteredOn(i -> "LEITE COND.ITALAC TP 395G".equals(i.rawDescription())).hasSize(2);

        FiscalItemResponse weighted = doc.items().get(0);
        assertThat(weighted.supplierItemCode()).isEqualTo("169196");
        assertThat(weighted.quantity()).isEqualByComparingTo("0.9944");
        assertThat(weighted.unit()).isEqualTo("KG");
        assertThat(weighted.unitPrice()).isEqualByComparingTo("10.750201");
        assertThat(weighted.totalPrice()).isEqualByComparingTo("10.69");

        assertThat(doc.items()).allSatisfy(i -> {
            assertThat(i.gtin()).isNull();
            assertThat(i.ncm()).isNull();
            assertThat(i.cest()).isNull();
        });

        assertThat(doc.payments()).extracting(FiscalPaymentResponse::method)
            .containsExactly("Cartão de Débito", "Cartão de Crédito");
        assertThat(doc.payments().get(0).amount()).isEqualByComparingTo("313.26");
        assertThat(doc.payments().get(1).amount()).isEqualByComparingTo("1.97");
    }

    @Test
    void reImportingTheSameQrCodeIsIdempotentAndDoesNotRefetch() {
        ResponseEntity<FiscalDocumentResponse> first = importAs(validAccessToken(), QR_URL);
        ResponseEntity<FiscalDocumentResponse> second = importAs(validAccessToken(), QR_URL);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());

        Integer rows = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM fiscal_document WHERE access_key = ?", Integer.class, ACCESS_KEY);
        assertThat(rows).isEqualTo(1);

        // The second call short-circuits on the stored access key — SEFAZ is hit once.
        verify(sefazSpHttpClient, times(1)).fetch(any());
    }

    @Test
    void anotherUserCannotRetrieveTheImportedDocument() {
        FiscalDocumentResponse mine = importAs(validAccessToken(), QR_URL).getBody();
        assertThat(mine).isNotNull();

        int status = client().get()
            .uri("/api/fiscal-documents/{id}", mine.id())
            .headers(h -> h.setBearerAuth(secondUserAccessToken()))
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(404);
    }

    @Test
    void rejectsAForeignHostWithoutContactingSefaz() {
        int status = importStatus(validAccessToken(),
            "https://evil.example.com/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p=" + ACCESS_KEY + "%7C2");
        assertThat(status).isEqualTo(422);
        verify(sefazSpHttpClient, never()).fetch(any());
    }

    @Test
    void rejectsPlainHttp() {
        assertThat(importStatus(validAccessToken(), QR_URL.replace("https://", "http://"))).isEqualTo(400);
        verify(sefazSpHttpClient, never()).fetch(any());
    }

    @Test
    void rejectsAMalformedQrPayload() {
        assertThat(importStatus(validAccessToken(),
            "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p=nope%7C2"))
            .isEqualTo(400);
    }

    @Test
    void rejectsGarbageAndUnknownPaths() {
        assertThat(importStatus(validAccessToken(), "not a url")).isEqualTo(400);
        assertThat(importStatus(validAccessToken(),
            "https://www.nfce.fazenda.sp.gov.br/outra/pagina.aspx?p=" + ACCESS_KEY + "%7C2")).isEqualTo(422);
    }

    @Test
    void rejectsUnauthenticatedImport() {
        int status = client().post().uri("/api/fiscal-documents/import")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new NfceImportRequest(QR_URL))
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(401);
    }
}
