package br.com.carrinhoesperto.fiscal.ingestion.saopaulo;

import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemRequest;
import br.com.carrinhoesperto.fiscal.ingestion.InvalidQrCodeUrlException;
import br.com.carrinhoesperto.fiscal.ingestion.InvalidQrPayloadException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Live</strong> end-to-end tests that actually call the São Paulo SEFAZ
 * NFC-e public consultation over the network — the real
 * {@link SefazSpHttpClient} + {@link SaoPauloNfceParser}, no mocks, no Spring.
 *
 * <p>Opt-in only, so the normal build stays offline and deterministic:
 * <pre>
 *   mvn -f backend/pom.xml test -Dtest=SefazSpLiveIntegrationTest -Dnfce.live=true
 *   # optionally point at your own receipt:
 *   -Dnfce.live.url='https://www.nfce.fazenda.sp.gov.br/qrcode?p=&lt;chave&gt;|2|1|1|&lt;hash&gt;'
 * </pre>
 * The default URL is the receipt the offline fixtures were captured from; SEFAZ
 * keeps consultations available for a long time, but if it 404s, pass a fresh
 * {@code -Dnfce.live.url}.
 */
@EnabledIfSystemProperty(named = "nfce.live", matches = "true")
class SefazSpLiveIntegrationTest {

    private static final String DEFAULT_QR_URL =
        "https://www.nfce.fazenda.sp.gov.br/qrcode?p="
            + "35260900371046000117650080001172161001384659%7C2%7C1%7C1%7C16AA3D2FF0C03038550613411FF3B328615A3F80";

    private final SaoPauloNfceQrCodeValidator validator = new SaoPauloNfceQrCodeValidator();
    private final SaoPauloNfceProvider provider = new SaoPauloNfceProvider(
        validator,
        new SefazSpHttpClient(new SefazSpProperties(null, null, 0, null), validator),
        new SaoPauloNfceParser());

    private static URI qrUrl() {
        return URI.create(System.getProperty("nfce.live.url", DEFAULT_QR_URL));
    }

    private static URI onPath(String path) {
        URI u = qrUrl();
        return URI.create("https://" + u.getHost() + path + "?" + u.getRawQuery());
    }

    private void assertLooksLikeARealReceipt(FiscalDocumentRequest doc) {
        assertThat(doc.accessKey()).hasSize(44).containsPattern("\\d{44}");
        assertThat(doc.issuer().cnpj()).hasSize(14);
        assertThat(doc.issuer().legalName()).isNotBlank();
        assertThat(doc.issuedAt()).isNotBlank();
        assertThat(doc.totalAmount()).isNotNull();
        assertThat(doc.totalAmount().signum()).isPositive();
        assertThat(doc.items()).isNotEmpty();
        assertThat(doc.items()).allSatisfy(item -> {
            assertThat(item.rawDescription()).isNotBlank();
            assertThat(item.quantity().signum()).isNotNegative();
            assertThat(item.unitPrice().signum()).isNotNegative();
            assertThat(item.totalPrice().signum()).isNotNegative();
            assertThat(item.unit()).isNotBlank();
        });
    }

    // ── Happy path: the URL shapes a real QR / user can produce ─────────────

    @Test
    void fetchesAndParsesFromTheShortQrCodeEntryUrl() {
        FiscalDocumentRequest doc = provider.fetchAndParse(qrUrl());
        assertLooksLikeARealReceipt(doc);
        System.out.printf("[live] /qrcode -> %d items, total %s, issuer %s (%s)%n",
            doc.items().size(), doc.totalAmount(), doc.issuer().legalName(), doc.issuer().cnpj());
        doc.items().forEach(i -> System.out.printf("  - %-34s %s x %s = %s%n",
            i.rawDescription(), i.quantity(), i.unitPrice(), i.totalPrice()));
    }

    @Test
    void fetchesAndParsesFromTheCanonicalConsultationUrl() {
        assertLooksLikeARealReceipt(
            provider.fetchAndParse(onPath("/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx")));
    }

    @Test
    void fetchesAndParsesFromALowerCasedPathViaOneRedirect() {
        assertLooksLikeARealReceipt(
            provider.fetchAndParse(onPath("/nfceconsultapublica/paginas/consultaqrcode.aspx")));
    }

    @Test
    void peeksTheAccessKeyFromTheLiveUrlWithoutFetching() {
        assertThat(provider.peekAccessKey(qrUrl())).hasSize(44);
        assertThat(provider.supports(qrUrl())).isTrue();
    }

    // ── The allow-list / SSRF boundary still holds (no network hit) ─────────

    @Test
    void rejectsAnAccessKeyOfTheWrongLength() {
        URI u = qrUrl();
        URI bad = URI.create("https://" + u.getHost() + u.getPath() + "?p=123%7C2");
        assertThatThrownBy(() -> provider.peekAccessKey(bad))
            .isInstanceOf(InvalidQrPayloadException.class);
    }

    @Test
    void rejectsAForeignHost() {
        URI foreign = URI.create("https://evil.example.com/qrcode?" + qrUrl().getRawQuery());
        assertThat(provider.supports(foreign)).isFalse();
        assertThatThrownBy(() -> provider.fetchAndParse(foreign))
            .isInstanceOf(InvalidQrCodeUrlException.class);
    }

    @Test
    void rejectsPlainHttp() {
        URI http = URI.create(qrUrl().toString().replace("https://", "http://"));
        assertThatThrownBy(() -> provider.fetchAndParse(http))
            .isInstanceOf(InvalidQrCodeUrlException.class);
    }

    @Test
    void reportsItemCountAndDescriptionsForManualComparison() {
        FiscalDocumentRequest doc = provider.fetchAndParse(qrUrl());
        assertThat(doc.items()).extracting(FiscalItemRequest::rawDescription).doesNotContainNull();
    }
}
