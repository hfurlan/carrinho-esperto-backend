package br.com.carrinhoesperto.session;

import br.com.carrinhoesperto.AbstractAuthenticatedIntegrationTest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentSummaryResponse;
import br.com.carrinhoesperto.fiscal.ingestion.dto.NfceImportRequest;
import br.com.carrinhoesperto.fiscal.ingestion.saopaulo.SefazSpHttpClient;
import br.com.carrinhoesperto.session.dto.AnonymousSessionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Anonymous trial (M006.1, docs/anonymous-trial.md): a visitor with no Cognito
 * account starts a session, imports an NFC-e and reads it back — while their data
 * stays isolated from other anonymous sessions, from authenticated users, and
 * from the admin surface. The outbound SEFAZ call is mocked with the sanitized
 * fixture, exactly as {@code NfceImportIntegrationTest}.
 */
class AnonymousTrialIntegrationTest extends AbstractAuthenticatedIntegrationTest {

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
        AnonymousSessionResponse body = client().post().uri("/api/anonymous-sessions")
            .retrieve()
            .body(AnonymousSessionResponse.class);
        assertThat(body).isNotNull();
        assertThat(body.token()).isNotBlank();
        return body.token();
    }

    private FiscalDocumentResponse importAsAnon(String token) {
        return client().post().uri("/api/fiscal-documents/import")
            .header("X-Anon-Session", token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new NfceImportRequest(QR_URL))
            .retrieve()
            .body(FiscalDocumentResponse.class);
    }

    private int anonGetStatus(String path, String token) {
        return client().get().uri(path)
            .header("X-Anon-Session", token)
            .exchange((req, res) -> res.getStatusCode().value());
    }

    // ── Session creation ────────────────────────────────────────────────────

    @Test
    void createsAFreshOpaqueTokenEachTime() {
        String first = newAnonymousToken();
        String second = newAnonymousToken();
        assertThat(first).isNotEqualTo(second);
    }

    // ── Import + readback, no account ───────────────────────────────────────

    @Test
    void anonymousUserCanImportAndReadTheReceipt() {
        String token = newAnonymousToken();

        FiscalDocumentResponse imported = importAsAnon(token);
        assertThat(imported).isNotNull();
        assertThat(imported.accessKey()).isEqualTo(ACCESS_KEY);
        assertThat(imported.items()).hasSize(22);

        FiscalDocumentResponse fetched = client().get()
            .uri("/api/fiscal-documents/{id}", imported.id())
            .header("X-Anon-Session", token)
            .retrieve().body(FiscalDocumentResponse.class);
        assertThat(fetched).isNotNull();
        assertThat(fetched.id()).isEqualTo(imported.id());

        // Owned by an ANONYMOUS app_user row, and last_seen_at was stamped by the import.
        Integer rows = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM fiscal_document d JOIN app_user u ON u.id = d.owner_user_id "
                + "WHERE d.id = ? AND u.kind = 'ANONYMOUS' AND u.last_seen_at IS NOT NULL",
            Integer.class, imported.id());
        assertThat(rows).isEqualTo(1);
    }

    // ── Isolation between anonymous sessions ────────────────────────────────

    @Test
    void anotherAnonymousSessionCannotSeeTheFirstSessionsDocument() {
        String tokenA = newAnonymousToken();
        FiscalDocumentResponse mine = importAsAnon(tokenA);
        assertThat(mine).isNotNull();

        String tokenB = newAnonymousToken();
        assertThat(anonGetStatus("/api/fiscal-documents/" + mine.id(), tokenB)).isEqualTo(404);

        List<FiscalDocumentSummaryResponse> listB = client().get().uri("/api/fiscal-documents")
            .header("X-Anon-Session", tokenB)
            .retrieve().body(new ParameterizedTypeReference<>() { });
        assertThat(listB).isEmpty();
    }

    // ── Isolation between anonymous and authenticated ───────────────────────

    @Test
    void anonymousAndAuthenticatedDataAreMutuallyInvisible() {
        String token = newAnonymousToken();
        FiscalDocumentResponse anonDoc = importAsAnon(token);
        assertThat(anonDoc).isNotNull();

        FiscalDocumentResponse userDoc = client().post().uri("/api/fiscal-documents/import")
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new NfceImportRequest(QR_URL))
            .retrieve().body(FiscalDocumentResponse.class);
        assertThat(userDoc).isNotNull();
        assertThat(userDoc.id()).isNotEqualTo(anonDoc.id());

        // Authenticated user cannot read the anonymous session's document.
        int userReadingAnon = client().get().uri("/api/fiscal-documents/{id}", anonDoc.id())
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(userReadingAnon).isEqualTo(404);

        // Anonymous session cannot read the authenticated user's document.
        assertThat(anonGetStatus("/api/fiscal-documents/" + userDoc.id(), token)).isEqualTo(404);
    }

    // ── Admin stays Cognito/role protected ─────────────────────────────────

    @Test
    void anonymousSessionCannotReachTheAdminSurface() {
        String token = newAnonymousToken();
        assertThat(anonGetStatus("/api/admin/dashboard", token)).isEqualTo(403);
    }

    // ── Bad / missing credentials ─────────────────────────────────────────

    @Test
    void anUnknownAnonSessionTokenIsUnauthorized() {
        assertThat(anonGetStatus("/api/fiscal-documents", "not-a-real-token")).isEqualTo(401);

        int noCredentials = client().get().uri("/api/fiscal-documents")
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(noCredentials).isEqualTo(401);
    }

    // ── QR SSRF controls still apply to anonymous imports ─────────────────

    @Test
    void anonymousImportOfAForeignHostIsRejectedWithoutContactingSefaz() {
        String token = newAnonymousToken();
        int status = client().post().uri("/api/fiscal-documents/import")
            .header("X-Anon-Session", token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new NfceImportRequest(
                "https://evil.example.com/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p=" + ACCESS_KEY + "%7C2"))
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(422);
        verify(sefazSpHttpClient, never()).fetch(any());
    }

    @Test
    void anonymousSessionEndpointItselfNeedsNoCredentials() {
        int status = client().post().uri("/api/anonymous-sessions")
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(HttpStatus.CREATED.value());
    }

    @Test
    void unknownDocumentIdIsAConsistent404ForAnonymous() {
        String token = newAnonymousToken();
        assertThat(anonGetStatus("/api/fiscal-documents/" + UUID.randomUUID(), token)).isEqualTo(404);
    }
}
