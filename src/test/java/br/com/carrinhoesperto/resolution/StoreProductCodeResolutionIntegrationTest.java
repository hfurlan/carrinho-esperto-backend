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
import br.com.carrinhoesperto.resolution.dto.StoreProductCodeRequest;
import br.com.carrinhoesperto.resolution.dto.StoreProductCodeResponse;
import br.com.carrinhoesperto.resolution.dto.StoreProductCodeUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M007 — deterministic {@code (store, cProd)} resolution. Exercises the new
 * {@code STORE_PRODUCT_CODE} method end to end: mapping CRUD (admin only),
 * store isolation, GTIN precedence, conflict rejection, admin re-resolution,
 * evidence, idempotency, and that Fiscal truth is never rewritten.
 */
class StoreProductCodeResolutionIntegrationTest extends AbstractAuthenticatedIntegrationTest {

    private static final String STORE_A = "00371046000117";
    private static final String STORE_B = "11444777000161";
    private static final String GTIN = "7891234567895";
    private static int accessKeySeq = 700;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM store_product_code");
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

    private UUID createProduct(String canonicalName, String gtin) {
        return client().post().uri("/api/catalog/products")
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ProductRequest(canonicalName, null, null, null, null, null, gtin, null, null, null))
            .retrieve().body(ProductResponse.class)
            .id();
    }

    /** A one-item receipt owned by the primary user. */
    private FiscalDocumentResponse createDoc(String issuerCnpj, String cProd, String cean) {
        FiscalItemRequest item = new FiscalItemRequest(
            null, "ARROZ T1 5KG CAMIL", cean, null, cProd,
            new BigDecimal("1"), "UN", new BigDecimal("24.90"), new BigDecimal("24.90"),
            null, null, null);
        FiscalDocumentRequest doc = new FiscalDocumentRequest(
            nextAccessKey(), 1L, 1, "2026-09-06T12:00:00-03:00",
            new FiscalIssuerRequest(issuerCnpj, "LOJA " + issuerCnpj, null, null),
            new BigDecimal("24.90"), new BigDecimal("24.90"), null, List.of(item), List.of());
        return client().post().uri("/api/fiscal-documents")
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(doc)
            .retrieve().body(FiscalDocumentResponse.class);
    }

    private StoreProductCodeResponse createMapping(String storeId, String code, UUID productId) {
        return client().post().uri("/api/admin/store-product-codes")
            .headers(h -> h.setBearerAuth(adminAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new StoreProductCodeRequest(storeId, code, productId))
            .retrieve().body(StoreProductCodeResponse.class);
    }

    private int createMappingStatus(String token, String storeId, String code, UUID productId) {
        return client().post().uri("/api/admin/store-product-codes")
            .headers(h -> { if (token != null) h.setBearerAuth(token); })
            .contentType(MediaType.APPLICATION_JSON)
            .body(new StoreProductCodeRequest(storeId, code, productId))
            .exchange((req, res) -> res.getStatusCode().value());
    }

    private List<br.com.carrinhoesperto.resolution.dto.DocumentItemResolutionResponse> reResolveAsAdmin(UUID documentId) {
        return client().post().uri("/api/admin/fiscal-documents/{id}/resolutions", documentId)
            .headers(h -> h.setBearerAuth(adminAccessToken()))
            .retrieve().body(new ParameterizedTypeReference<>() { });
    }

    private ResolutionResponse resolutionOf(UUID fiscalItemId) {
        return client().post().uri("/api/fiscal-items/{id}/resolution", fiscalItemId)
            .headers(h -> h.setBearerAuth(validAccessToken()))
            .retrieve().body(ResolutionResponse.class);
    }

    // ── exact mapping + evidence + re-resolution + immutability ────────────

    @Test
    void anAdminMappingResolvesTheItemByStoreProductCodeWithEvidenceAndLeavesFiscalUnchanged() {
        UUID productId = createProduct("Arroz Camil Tipo 1 5 kg", null);
        FiscalDocumentResponse doc = createDoc(STORE_A, "ABC123", null);
        FiscalItemResponse item = doc.items().get(0);

        // Initially unresolved (no GTIN, no mapping yet).
        assertThat(resolutionOf(item.id()).status()).isEqualTo("UNRESOLVED");

        createMapping(STORE_A, "ABC123", productId);
        UUID documentId = doc.id();
        reResolveAsAdmin(documentId);

        ResolutionResponse resolution = resolutionOf(item.id());
        assertThat(resolution.status()).isEqualTo("RESOLVED");
        assertThat(resolution.method()).isEqualTo("STORE_PRODUCT_CODE");
        assertThat(resolution.canonicalProductId()).isEqualTo(productId);
        assertThat(resolution.evidence()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo("STORE_PRODUCT_CODE");
            assertThat(e.source()).isEqualTo("FISCAL_ITEM");
            assertThat(e.value()).isEqualTo("ABC123");
            assertThat(e.metadata()).isEqualTo(STORE_A);
        });

        // Fiscal truth untouched.
        String rawDescription = jdbcTemplate.queryForObject(
            "SELECT raw_description FROM fiscal_item WHERE id = ?", String.class, item.id());
        assertThat(rawDescription).isEqualTo("ARROZ T1 5KG CAMIL");
    }

    // ── store isolation (M007 §14) ───────────────────────────────────────

    @Test
    void theSameCodeMapsIndependentlyPerStore() {
        UUID productA = createProduct("Arroz Camil Tipo 1 5 kg", null);
        UUID productB = createProduct("Feijão Carioca Camil 1 kg", null);
        createMapping(STORE_A, "123", productA);
        createMapping(STORE_B, "123", productB);

        FiscalDocumentResponse docA = createDoc(STORE_A, "123", null);
        FiscalDocumentResponse docB = createDoc(STORE_B, "123", null);
        FiscalItemResponse fromA = docA.items().get(0);
        FiscalItemResponse fromB = docB.items().get(0);
        reResolveAsAdmin(docA.id());
        reResolveAsAdmin(docB.id());

        assertThat(resolutionOf(fromA.id()).canonicalProductId()).isEqualTo(productA);
        assertThat(resolutionOf(fromB.id()).canonicalProductId()).isEqualTo(productB);
    }

    @Test
    void anUnknownStoreCodeStaysUnresolved() {
        createProduct("Arroz Camil Tipo 1 5 kg", null);
        FiscalDocumentResponse doc = createDoc(STORE_A, "NO-MAPPING", null);
        FiscalItemResponse item = doc.items().get(0);
        reResolveAsAdmin(doc.id());
        assertThat(resolutionOf(item.id()).status()).isEqualTo("UNRESOLVED");
    }

    // ── GTIN precedence (M007 §7) ────────────────────────────────────────

    @Test
    void anExactGtinMatchBeatsAStoreProductCodeMapping() {
        UUID gtinProduct = createProduct("Arroz Camil Tipo 1 5 kg", GTIN);
        UUID codeProduct = createProduct("Something Else 1 kg", null);
        createMapping(STORE_A, "ABC123", codeProduct);

        FiscalDocumentResponse doc = createDoc(STORE_A, "ABC123", GTIN);
        FiscalItemResponse item = doc.items().get(0);
        reResolveAsAdmin(doc.id());

        ResolutionResponse resolution = resolutionOf(item.id());
        assertThat(resolution.method()).isEqualTo("GTIN_EXACT");
        assertThat(resolution.canonicalProductId()).isEqualTo(gtinProduct);
    }

    // ── conflict + update (M007 §13) ─────────────────────────────────────

    @Test
    void aConflictingMappingIsRejectedNotOverwritten() {
        UUID productA = createProduct("Arroz Camil Tipo 1 5 kg", null);
        UUID productB = createProduct("Feijão Carioca Camil 1 kg", null);
        createMapping(STORE_A, "DUP", productA);

        assertThat(createMappingStatus(adminAccessToken(), STORE_A, "DUP", productB)).isEqualTo(409);

        // still points at A
        List<StoreProductCodeResponse> all = client().get().uri("/api/admin/store-product-codes?storeId={s}", STORE_A)
            .headers(h -> h.setBearerAuth(adminAccessToken()))
            .retrieve().body(new ParameterizedTypeReference<>() { });
        assertThat(all).singleElement()
            .satisfies(m -> assertThat(m.canonicalProductId()).isEqualTo(productA));
    }

    @Test
    void updatingAMappingRepointsItAndReResolutionReflectsTheChange() {
        UUID productA = createProduct("Arroz Camil Tipo 1 5 kg", null);
        UUID productB = createProduct("Feijão Carioca Camil 1 kg", null);
        StoreProductCodeResponse mapping = createMapping(STORE_A, "SWAP", productA);
        FiscalDocumentResponse doc = createDoc(STORE_A, "SWAP", null);
        FiscalItemResponse item = doc.items().get(0);
        UUID documentId = doc.id();

        reResolveAsAdmin(documentId);
        assertThat(resolutionOf(item.id()).canonicalProductId()).isEqualTo(productA);

        client().put().uri("/api/admin/store-product-codes/{id}", mapping.id())
            .headers(h -> h.setBearerAuth(adminAccessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new StoreProductCodeUpdateRequest(productB))
            .retrieve().toBodilessEntity();

        reResolveAsAdmin(documentId);
        assertThat(resolutionOf(item.id()).canonicalProductId()).isEqualTo(productB);
    }

    @Test
    void deletingAMappingRevertsTheItemOnTheNextReResolution() {
        UUID productId = createProduct("Arroz Camil Tipo 1 5 kg", null);
        StoreProductCodeResponse mapping = createMapping(STORE_A, "DELME", productId);
        FiscalDocumentResponse doc = createDoc(STORE_A, "DELME", null);
        FiscalItemResponse item = doc.items().get(0);
        UUID documentId = doc.id();

        reResolveAsAdmin(documentId);
        assertThat(resolutionOf(item.id()).status()).isEqualTo("RESOLVED");

        client().delete().uri("/api/admin/store-product-codes/{id}", mapping.id())
            .headers(h -> h.setBearerAuth(adminAccessToken()))
            .retrieve().toBodilessEntity();

        reResolveAsAdmin(documentId);
        assertThat(resolutionOf(item.id()).status()).isEqualTo("UNRESOLVED");
    }

    // ── idempotency (M007 §24) ──────────────────────────────────────────

    @Test
    void repeatedReResolutionKeepsOneResolutionRowAndOneEvidenceRow() {
        UUID productId = createProduct("Arroz Camil Tipo 1 5 kg", null);
        FiscalDocumentResponse doc = createDoc(STORE_A, "IDEM", null);
        FiscalItemResponse item = doc.items().get(0);
        createMapping(STORE_A, "IDEM", productId);
        UUID documentId = doc.id();

        reResolveAsAdmin(documentId);
        reResolveAsAdmin(documentId);
        reResolveAsAdmin(documentId);

        Integer resolutions = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM product_resolution WHERE fiscal_item_id = ?", Integer.class, item.id());
        assertThat(resolutions).isEqualTo(1);
        Integer evidence = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM resolution_evidence e JOIN product_resolution r ON r.id = e.resolution_id "
                + "WHERE r.fiscal_item_id = ?", Integer.class, item.id());
        assertThat(evidence).isEqualTo(1);
        Integer mappings = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM store_product_code WHERE store_id = ? AND code = ?",
            Integer.class, STORE_A, "IDEM");
        assertThat(mappings).isEqualTo(1);
    }

    // ── security / ownership (M007 §24) ─────────────────────────────────

    @Test
    void ordinaryUsersCannotManageStoreVocabulary() {
        UUID productId = createProduct("Arroz Camil Tipo 1 5 kg", null);
        FiscalDocumentResponse doc = createDoc(STORE_A, "SEC", null);
        FiscalItemResponse item = doc.items().get(0);
        UUID documentId = doc.id();
        StoreProductCodeResponse mapping = createMapping(STORE_A, "SEC", productId);

        for (String token : new String[] {validAccessToken(), secondUserAccessToken()}) {
            assertThat(adminGet("/api/admin/store-product-codes", token)).isEqualTo(403);
            assertThat(createMappingStatus(token, STORE_A, "X-" + token.hashCode(), productId)).isEqualTo(403);
            assertThat(adminVerb(HttpMethod.PUT, "/api/admin/store-product-codes/" + mapping.id(), token,
                new StoreProductCodeUpdateRequest(productId))).isEqualTo(403);
            assertThat(adminVerb(HttpMethod.DELETE, "/api/admin/store-product-codes/" + mapping.id(), token, null))
                .isEqualTo(403);
            assertThat(adminVerb(HttpMethod.POST, "/api/admin/fiscal-documents/" + documentId + "/resolutions",
                token, null)).isEqualTo(403);
        }
    }

    // ── validation / not-found ─────────────────────────────────────────

    @Test
    void createRejectsBadInput() {
        UUID productId = createProduct("Arroz Camil Tipo 1 5 kg", null);
        assertThat(createMappingStatus(adminAccessToken(), "123", "X", productId)).isEqualTo(400); // not 14 digits
        assertThat(createMappingStatus(adminAccessToken(), STORE_A, "  ", productId)).isEqualTo(400); // blank code
        assertThat(createMappingStatus(adminAccessToken(), STORE_A, "X", UUID.randomUUID())).isEqualTo(404); // no product
    }

    @Test
    void reResolvingAnUnknownDocumentIs404() {
        assertThat(adminVerb(HttpMethod.POST,
            "/api/admin/fiscal-documents/" + UUID.randomUUID() + "/resolutions", adminAccessToken(), null))
            .isEqualTo(404);
    }

    private int adminGet(String path, String token) {
        return client().get().uri(path)
            .headers(h -> { if (token != null) h.setBearerAuth(token); })
            .exchange((req, res) -> res.getStatusCode().value());
    }

    private int adminVerb(HttpMethod method, String path, String token, Object body) {
        return client().method(method).uri(path)
            .headers(h -> {
                if (token != null) h.setBearerAuth(token);
                if (body != null) h.setContentType(MediaType.APPLICATION_JSON);
            })
            .body(body == null ? "" : body)
            .exchange((req, res) -> res.getStatusCode().value());
    }
}
