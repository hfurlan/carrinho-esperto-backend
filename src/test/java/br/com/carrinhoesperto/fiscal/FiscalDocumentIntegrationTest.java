package br.com.carrinhoesperto.fiscal;

import br.com.carrinhoesperto.AbstractAuthenticatedIntegrationTest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentSummaryResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalIssuerRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalPaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
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
 * Fiscal Document Core (M002, docs/fiscal-core.md): import a fiscal-document
 * payload and read it back — end to end over real HTTP, Postgres and a real
 * Cognito identity. Proves that fiscal truth (raw descriptions, fiscal
 * identifiers, "SEM GTIN", cProd) is stored verbatim, that documents are
 * user-owned, and that re-importing the same NFC-e is idempotent.
 */
class FiscalDocumentIntegrationTest extends AbstractAuthenticatedIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        // ON DELETE CASCADE clears fiscal_item / fiscal_payment.
        jdbcTemplate.update("DELETE FROM fiscal_document");
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    /** A 44-digit access key, unique per seed. */
    private static String accessKey(int seed) {
        return String.format("%044d", seed);
    }

    private static FiscalIssuerRequest issuer() {
        return new FiscalIssuerRequest(
            "12.345.678/0001-99", "SUPERMERCADO EXEMPLO LTDA", "Mercado Exemplo",
            "Av. das Compras, 100 - Centro - São Paulo/SP");
    }

    /** Two items (a real GTIN line + a "SEM GTIN" line) and two payment lines. */
    private static FiscalDocumentRequest fullRequest(String accessKey) {
        FiscalItemRequest milk = new FiscalItemRequest(
            null, "LEITE ITALAC INT 1L", "7891234567890", "7891234567890", "000123",
            new BigDecimal("2"), "UN", new BigDecimal("5.49"), new BigDecimal("10.98"),
            null, "04012010", "1234567");
        FiscalItemRequest nescau = new FiscalItemRequest(
            null, "NESCAU 2.0 350G", "SEM GTIN", "SEM GTIN", "000456",
            new BigDecimal("1"), "UN", new BigDecimal("8.90"), new BigDecimal("8.90"),
            new BigDecimal("0.50"), null, null);
        return new FiscalDocumentRequest(
            accessKey, 12345L, 1, "2024-05-01T18:30:00-03:00", issuer(),
            new BigDecimal("19.38"), new BigDecimal("19.88"), new BigDecimal("0.50"),
            List.of(milk, nescau),
            List.of(
                new FiscalPaymentRequest("03", "Cartão de crédito", new BigDecimal("15.00")),
                new FiscalPaymentRequest("01", "Dinheiro", new BigDecimal("4.38"))));
    }

    private ResponseEntity<FiscalDocumentResponse> post(String token, FiscalDocumentRequest request) {
        return client().post().uri("/api/fiscal-documents")
            .headers(h -> h.setBearerAuth(token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toEntity(FiscalDocumentResponse.class);
    }

    private int postStatus(String token, FiscalDocumentRequest request) {
        return client().post().uri("/api/fiscal-documents")
            .headers(h -> h.setBearerAuth(token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange((req, res) -> res.getStatusCode().value());
    }

    // ── Creation & retrieval ────────────────────────────────────────────────

    @Test
    void createsAndRetrievesADocumentWithMultipleItemsAndPayments() {
        FiscalDocumentResponse created = post(validAccessToken(), fullRequest(accessKey(1))).getBody();

        assertThat(created).isNotNull();
        assertThat(created.items()).hasSize(2);
        assertThat(created.payments()).hasSize(2);
        assertThat(created.accessKey()).isEqualTo(accessKey(1));

        FiscalDocumentResponse fetched = client().get()
            .uri("/api/fiscal-documents/{id}", created.id())
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve()
            .body(FiscalDocumentResponse.class);

        assertThat(fetched).isNotNull();
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.items()).extracting(FiscalItemResponse::rawDescription)
            .containsExactly("LEITE ITALAC INT 1L", "NESCAU 2.0 350G");
        assertThat(fetched.items()).extracting(FiscalItemResponse::lineNumber).containsExactly(1, 2);

        Integer ownerCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM fiscal_document WHERE id = ? AND owner_user_id IN "
                + "(SELECT id FROM app_user WHERE cognito_subject = ?)",
            Integer.class, created.id(), expectedSubject());
        assertThat(ownerCount).isEqualTo(1);
    }

    @Test
    void preservesRawDescriptionExactly() {
        FiscalItemRequest item = new FiscalItemRequest(
            null, "LEITE ITALAC INT 1L", null, null, null,
            new BigDecimal("1"), "UN", new BigDecimal("5.49"), new BigDecimal("5.49"),
            null, null, null);
        FiscalDocumentRequest request = new FiscalDocumentRequest(
            accessKey(2), null, null, "2024-05-01T18:30:00-03:00", issuer(),
            new BigDecimal("5.49"), null, null, List.of(item), List.of());

        FiscalDocumentResponse created = post(validAccessToken(), request).getBody();

        assertThat(created).isNotNull();
        assertThat(created.items().get(0).rawDescription()).isEqualTo("LEITE ITALAC INT 1L");

        String stored = jdbcTemplate.queryForObject(
            "SELECT raw_description FROM fiscal_item WHERE fiscal_document_id = ?",
            String.class, created.id());
        assertThat(stored).isEqualTo("LEITE ITALAC INT 1L");
    }

    @Test
    void preservesFiscalIdentifiers() {
        FiscalDocumentResponse created = post(validAccessToken(), fullRequest(accessKey(3))).getBody();

        assertThat(created).isNotNull();
        FiscalItemResponse milk = created.items().get(0);
        assertThat(milk.gtin()).isEqualTo("7891234567890");
        assertThat(milk.gtinTaxable()).isEqualTo("7891234567890");
        assertThat(milk.supplierItemCode()).isEqualTo("000123");
        assertThat(milk.ncm()).isEqualTo("04012010");
        assertThat(milk.cest()).isEqualTo("1234567");
    }

    @Test
    void preservesTheSemGtinLiteralInsteadOfNull() {
        FiscalDocumentResponse created = post(validAccessToken(), fullRequest(accessKey(4))).getBody();

        assertThat(created).isNotNull();
        FiscalItemResponse nescau = created.items().get(1);
        assertThat(nescau.gtin()).isEqualTo("SEM GTIN");
        assertThat(nescau.gtinTaxable()).isEqualTo("SEM GTIN");

        String storedGtin = jdbcTemplate.queryForObject(
            "SELECT gtin FROM fiscal_item WHERE fiscal_document_id = ? AND line_number = 2",
            String.class, created.id());
        assertThat(storedGtin).isEqualTo("SEM GTIN");
    }

    @Test
    void persistsPaymentInformation() {
        FiscalDocumentResponse created = post(validAccessToken(), fullRequest(accessKey(5))).getBody();

        assertThat(created).isNotNull();
        assertThat(created.payments()).extracting(p -> p.method() + ":" + p.amount().stripTrailingZeros().toPlainString())
            .containsExactly("03:15", "01:4.38");
        assertThat(created.payments().get(0).methodDescription()).isEqualTo("Cartão de crédito");
    }

    @Test
    void optionalFiscalFieldsMayBeAbsentWithoutFabricatingValues() {
        FiscalItemRequest bare = new FiscalItemRequest(
            null, "ARROZ TIPO 1 5KG", null, null, null,
            new BigDecimal("1"), "UN", new BigDecimal("24.90"), new BigDecimal("24.90"),
            null, null, null);
        FiscalDocumentRequest request = new FiscalDocumentRequest(
            accessKey(6), null, null, "2024-05-01T18:30:00-03:00", issuer(),
            new BigDecimal("24.90"), null, null, List.of(bare), List.of());

        FiscalDocumentResponse created = post(validAccessToken(), request).getBody();

        assertThat(created).isNotNull();
        assertThat(created.documentNumber()).isNull();
        assertThat(created.series()).isNull();
        assertThat(created.totalItemsAmount()).isNull();
        assertThat(created.totalDiscount()).isNull();
        FiscalItemResponse item = created.items().get(0);
        assertThat(item.gtin()).isNull();
        assertThat(item.gtinTaxable()).isNull();
        assertThat(item.supplierItemCode()).isNull();
        assertThat(item.discount()).isNull();
        assertThat(item.ncm()).isNull();
        assertThat(item.cest()).isNull();
    }

    // ── Ownership ───────────────────────────────────────────────────────────

    @Test
    void aUserCannotRetrieveAnotherUsersFiscalDocument() {
        FiscalDocumentResponse mine = post(validAccessToken(), fullRequest(accessKey(7))).getBody();
        assertThat(mine).isNotNull();

        int status = client().get()
            .uri("/api/fiscal-documents/{id}", mine.id())
            .headers(h -> h.setBearerAuth(secondUserAccessToken()))
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(404);

        List<FiscalDocumentSummaryResponse> othersList = client().get()
            .uri("/api/fiscal-documents")
            .headers(h -> h.setBearerAuth(secondUserAccessToken()))
            .retrieve()
            .body(new ParameterizedTypeReference<>() { });
        assertThat(othersList).isEmpty();
    }

    // ── Idempotency ─────────────────────────────────────────────────────────

    @Test
    void reImportingTheSameAccessKeyIsIdempotent() {
        ResponseEntity<FiscalDocumentResponse> first = post(validAccessToken(), fullRequest(accessKey(8)));
        ResponseEntity<FiscalDocumentResponse> second = post(validAccessToken(), fullRequest(accessKey(8)));

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());

        Integer docs = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM fiscal_document WHERE access_key = ?", Integer.class, accessKey(8));
        assertThat(docs).isEqualTo(1);
        Integer items = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM fiscal_item WHERE fiscal_document_id = ?",
            Integer.class, first.getBody().id());
        assertThat(items).isEqualTo(2);
    }

    @Test
    void differentAccessKeysFromTheSameIssuerAreDistinctDocuments() {
        post(validAccessToken(), fullRequest(accessKey(9)));
        post(validAccessToken(), fullRequest(accessKey(10)));

        List<FiscalDocumentSummaryResponse> list = client().get()
            .uri("/api/fiscal-documents")
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve()
            .body(new ParameterizedTypeReference<>() { });

        assertThat(list).hasSize(2);
        assertThat(list).extracting(FiscalDocumentSummaryResponse::accessKey)
            .containsExactlyInAnyOrder(accessKey(9), accessKey(10));
        assertThat(list).allSatisfy(s -> assertThat(s.itemCount()).isEqualTo(2));
    }

    // ── Validation & security ───────────────────────────────────────────────

    @Test
    void rejectsABlankRawDescription() {
        FiscalItemRequest blank = new FiscalItemRequest(
            null, "   ", null, null, null,
            new BigDecimal("1"), "UN", new BigDecimal("1.00"), new BigDecimal("1.00"),
            null, null, null);
        FiscalDocumentRequest request = new FiscalDocumentRequest(
            accessKey(11), null, null, "2024-05-01T18:30:00-03:00", issuer(),
            new BigDecimal("1.00"), null, null, List.of(blank), List.of());

        assertThat(postStatus(validAccessToken(), request)).isEqualTo(400);
    }

    @Test
    void rejectsAMissingAccessKey() {
        FiscalItemRequest item = new FiscalItemRequest(
            null, "ARROZ", null, null, null,
            new BigDecimal("1"), "UN", new BigDecimal("1.00"), new BigDecimal("1.00"),
            null, null, null);
        FiscalDocumentRequest request = new FiscalDocumentRequest(
            null, null, null, "2024-05-01T18:30:00-03:00", issuer(),
            new BigDecimal("1.00"), null, null, List.of(item), List.of());

        assertThat(postStatus(validAccessToken(), request)).isEqualTo(400);
    }

    @Test
    void rejectsAnAccessKeyThatIsNotFortyFourDigits() {
        FiscalItemRequest item = new FiscalItemRequest(
            null, "ARROZ", null, null, null,
            new BigDecimal("1"), "UN", new BigDecimal("1.00"), new BigDecimal("1.00"),
            null, null, null);
        FiscalDocumentRequest request = new FiscalDocumentRequest(
            "12345", null, null, "2024-05-01T18:30:00-03:00", issuer(),
            new BigDecimal("1.00"), null, null, List.of(item), List.of());

        assertThat(postStatus(validAccessToken(), request)).isEqualTo(400);
    }

    @Test
    void rejectsANegativeQuantity() {
        FiscalItemRequest negative = new FiscalItemRequest(
            null, "ARROZ", null, null, null,
            new BigDecimal("-1"), "UN", new BigDecimal("1.00"), new BigDecimal("1.00"),
            null, null, null);
        FiscalDocumentRequest request = new FiscalDocumentRequest(
            accessKey(12), null, null, "2024-05-01T18:30:00-03:00", issuer(),
            new BigDecimal("1.00"), null, null, List.of(negative), List.of());

        assertThat(postStatus(validAccessToken(), request)).isEqualTo(400);
    }

    @Test
    void rejectsADocumentWithNoItems() {
        FiscalDocumentRequest request = new FiscalDocumentRequest(
            accessKey(13), null, null, "2024-05-01T18:30:00-03:00", issuer(),
            new BigDecimal("0.00"), null, null, List.of(), List.of());

        assertThat(postStatus(validAccessToken(), request)).isEqualTo(400);
    }

    @Test
    void rejectsUnauthenticatedRequests() {
        int postStatus = client().post().uri("/api/fiscal-documents")
            .contentType(MediaType.APPLICATION_JSON)
            .body(fullRequest(accessKey(14)))
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(postStatus).isEqualTo(401);

        int getStatus = client().get().uri("/api/fiscal-documents/{id}", UUID.randomUUID())
            .exchange((req, res) -> res.getStatusCode().value());
        assertThat(getStatus).isEqualTo(401);
    }
}
