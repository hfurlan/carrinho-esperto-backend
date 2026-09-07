package br.com.carrinhoesperto.fiscal.ingestion.saopaulo;

import br.com.carrinhoesperto.fiscal.ingestion.InvalidQrCodeUrlException;
import br.com.carrinhoesperto.fiscal.ingestion.InvalidQrPayloadException;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the São Paulo NFC-e QR URL allow-list (M003 §6, §19). Covers
 * the SSRF boundary: only the exact official SEFAZ-SP endpoint, over HTTPS, with
 * a real 44-digit access key, is accepted.
 */
class SaoPauloNfceQrCodeValidatorTest {

    private static final String KEY = "35260900371046000117650080001172161001384659";
    private static final String REFERENCE_URL =
        "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx"
            + "?p=" + KEY + "%7C2%7C1%7C1%7C16AA3D2FF0C03038550613411FF3B328615A3F80";

    private final SaoPauloNfceQrCodeValidator validator = new SaoPauloNfceQrCodeValidator();

    @Test
    void acceptsTheProductionReferenceUrlAndExtractsTheAccessKey() {
        URI uri = URI.create(REFERENCE_URL);
        assertThat(validator.isSaoPauloConsultationUrl(uri)).isTrue();
        assertThat(validator.extractAccessKey(uri)).isEqualTo(KEY);
    }

    @Test
    void rejectsPlainHttp() {
        URI uri = URI.create(REFERENCE_URL.replace("https://", "http://"));
        // Host/path still match, so the provider is selected...
        assertThat(validator.isSaoPauloConsultationUrl(uri)).isTrue();
        // ...but extraction insists on HTTPS.
        assertThatThrownBy(() -> validator.extractAccessKey(uri))
            .isInstanceOf(InvalidQrCodeUrlException.class);
    }

    @Test
    void rejectsAForeignHost() {
        URI uri = URI.create(
            "https://evil.example.com/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p=" + KEY + "%7C2");
        assertThat(validator.isSaoPauloConsultationUrl(uri)).isFalse();
        assertThatThrownBy(() -> validator.extractAccessKey(uri))
            .isInstanceOf(InvalidQrCodeUrlException.class);
    }

    @Test
    void rejectsALookalikeSubdomain() {
        URI uri = URI.create(
            "https://www.nfce.fazenda.sp.gov.br.evil.example.com/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p="
                + KEY + "%7C2");
        assertThat(validator.isSaoPauloConsultationUrl(uri)).isFalse();
    }

    @Test
    void rejectsAWrongPath() {
        URI uri = URI.create(
            "https://www.nfce.fazenda.sp.gov.br/some/other/path.aspx?p=" + KEY + "%7C2");
        assertThat(validator.isSaoPauloConsultationUrl(uri)).isFalse();
        assertThatThrownBy(() -> validator.extractAccessKey(uri))
            .isInstanceOf(InvalidQrCodeUrlException.class);
    }

    @Test
    void rejectsANonStandardPort() {
        URI uri = URI.create(
            "https://www.nfce.fazenda.sp.gov.br:8443/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p="
                + KEY + "%7C2");
        assertThatThrownBy(() -> validator.extractAccessKey(uri))
            .isInstanceOf(InvalidQrCodeUrlException.class);
    }

    @Test
    void rejectsEmbeddedCredentials() {
        URI uri = URI.create(
            "https://user:pass@www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p="
                + KEY + "%7C2");
        assertThatThrownBy(() -> validator.extractAccessKey(uri))
            .isInstanceOf(InvalidQrCodeUrlException.class);
    }

    @Test
    void rejectsAMissingPParameter() {
        URI uri = URI.create(
            "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx");
        assertThatThrownBy(() -> validator.extractAccessKey(uri))
            .isInstanceOf(InvalidQrPayloadException.class);
    }

    @Test
    void rejectsAPPayloadWithoutAValidAccessKey() {
        URI uri = URI.create(
            "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p=not-a-key%7C2%7C1");
        assertThatThrownBy(() -> validator.extractAccessKey(uri))
            .isInstanceOf(InvalidQrPayloadException.class);
    }

    @Test
    void rejectsAnAccessKeyOfTheWrongLength() {
        URI uri = URI.create(
            "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p="
                + KEY.substring(0, 43) + "%7C2");
        assertThatThrownBy(() -> validator.extractAccessKey(uri))
            .isInstanceOf(InvalidQrPayloadException.class);
    }

    @Test
    void blocksHostsResolvingToLoopbackOrPrivateOrLinkLocalAddresses() {
        assertThatThrownBy(() -> validator.assertHostResolvesToPublicAddress(URI.create("https://127.0.0.1/x")))
            .isInstanceOf(InvalidQrCodeUrlException.class);
        assertThatThrownBy(() -> validator.assertHostResolvesToPublicAddress(URI.create("https://localhost/x")))
            .isInstanceOf(InvalidQrCodeUrlException.class);
        assertThatThrownBy(() -> validator.assertHostResolvesToPublicAddress(URI.create("https://10.1.2.3/x")))
            .isInstanceOf(InvalidQrCodeUrlException.class);
        assertThatThrownBy(() -> validator.assertHostResolvesToPublicAddress(URI.create("https://169.254.169.254/x")))
            .isInstanceOf(InvalidQrCodeUrlException.class);
    }
}
