package br.com.carrinhoesperto.fiscal.ingestion.saopaulo;

import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.ingestion.NfceProvider;
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
        byte[] html = httpClient.fetch(qrCodeUrl);
        return parser.parse(html, accessKey);
    }
}
