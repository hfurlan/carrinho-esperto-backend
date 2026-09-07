package br.com.carrinhoesperto.fiscal.ingestion.saopaulo;

import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.ingestion.NfceParseException;
import br.com.carrinhoesperto.fiscal.ingestion.NfceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * {@link NfceProvider} for São Paulo — the first and, for now, only supported
 * state (M003). São Paulo was chosen first because it publishes a stable public
 * HTML consultation at a well-known official endpoint and is a high-volume
 * retail state; the provider seam keeps the door open for other UFs without
 * touching the Fiscal model.
 */
@Component
public class SaoPauloNfceProvider implements NfceProvider {

    private static final Logger log = LoggerFactory.getLogger(SaoPauloNfceProvider.class);

    private final SaoPauloNfceQrCodeValidator validator;
    private final SefazSpHttpClient httpClient;
    private final SaoPauloNfceParser parser;

    SaoPauloNfceProvider(SaoPauloNfceQrCodeValidator validator,
                         SefazSpHttpClient httpClient,
                         SaoPauloNfceParser parser) {
        this.validator = validator;
        this.httpClient = httpClient;
        this.parser = parser;
    }

    @Override
    public boolean supports(URI qrCodeUrl) {
        return validator.isSaoPauloConsultationUrl(qrCodeUrl);
    }

    @Override
    public String peekAccessKey(URI qrCodeUrl) {
        return validator.extractAccessKey(qrCodeUrl);
    }

    @Override
    public FiscalDocumentRequest fetchAndParse(URI qrCodeUrl) {
        String accessKey = validator.extractAccessKey(qrCodeUrl);
        // A `/qrcode` short link is rewritten to the consultation page so the
        // fetch targets it directly (no redirect to follow).
        URI consultationUrl = validator.canonicalConsultationUri(qrCodeUrl);
        if (!consultationUrl.equals(qrCodeUrl)) {
            log.info("nfce import: canonicalised [{}] -> [{}]", qrCodeUrl, consultationUrl);
        }
        byte[] html = httpClient.fetch(consultationUrl);
        try {
            return parser.parse(html, accessKey);
        } catch (NfceParseException e) {
            // The fetch succeeded but the page did not look like the expected
            // consultation result (layout change, interstitial/challenge page,
            // login wall, …). Log the size and reason server-side; the client
            // still gets a generic message.
            log.warn("SEFAZ-SP consultation page could not be parsed (bytes={}, reason={})",
                html.length, e.getMessage());
            throw e;
        }
    }
}
