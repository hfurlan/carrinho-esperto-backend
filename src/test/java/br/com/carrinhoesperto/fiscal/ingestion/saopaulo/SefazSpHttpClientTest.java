package br.com.carrinhoesperto.fiscal.ingestion.saopaulo;

import br.com.carrinhoesperto.fiscal.ingestion.InvalidQrCodeUrlException;
import br.com.carrinhoesperto.fiscal.ingestion.SefazUnavailableException;
import br.com.carrinhoesperto.fiscal.ingestion.SefazUnexpectedResponseException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the SEFAZ-SP HTTP adapter's guard rails (M003 §8, §22): the
 * response-size cap, status classification, and the refusal to fetch anything
 * that is not an allow-listed SEFAZ-SP URL. The happy-path socket call is
 * exercised via the manual verification in docs/nfce-ingestion.md, not here —
 * automated tests must not depend on live SEFAZ, and the allow-list (by design)
 * makes a localhost test server unreachable.
 */
class SefazSpHttpClientTest {

    private final SefazSpHttpClient client =
        new SefazSpHttpClient(new SefazSpProperties(null, null, 0, null), new SaoPauloNfceQrCodeValidator());

    @Test
    void fetchRefusesAnyUrlThatIsNotAnAllowListedSefazSpUrl() {
        assertThatThrownBy(() -> client.fetch(URI.create("https://evil.example.com/whatever")))
            .isInstanceOf(InvalidQrCodeUrlException.class);
        assertThatThrownBy(() -> client.fetch(URI.create("http://169.254.169.254/latest/meta-data/")))
            .isInstanceOf(InvalidQrCodeUrlException.class);
    }

    @Test
    void readLimitedRejectsABodyOverTheCap() {
        byte[] tooBig = new byte[2048];
        assertThatThrownBy(() -> SefazSpHttpClient.readLimited(new ByteArrayInputStream(tooBig), 1024))
            .isInstanceOf(SefazUnexpectedResponseException.class);
    }

    @Test
    void readLimitedReturnsABodyWithinTheCap() throws Exception {
        byte[] body = "<html>ok</html>".getBytes();
        assertThat(SefazSpHttpClient.readLimited(new ByteArrayInputStream(body), 1024)).isEqualTo(body);
    }

    @Test
    void classifiesUpstreamStatuses() {
        assertThatCode(() -> SefazSpHttpClient.checkStatus(200)).doesNotThrowAnyException();
        assertThatThrownBy(() -> SefazSpHttpClient.checkStatus(302))
            .isInstanceOf(SefazUnexpectedResponseException.class);
        assertThatThrownBy(() -> SefazSpHttpClient.checkStatus(404))
            .isInstanceOf(SefazUnexpectedResponseException.class);
        assertThatThrownBy(() -> SefazSpHttpClient.checkStatus(429))
            .isInstanceOf(SefazUnavailableException.class);
        assertThatThrownBy(() -> SefazSpHttpClient.checkStatus(503))
            .isInstanceOf(SefazUnavailableException.class);
    }
}
