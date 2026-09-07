package br.com.carrinhoesperto.fiscal.ingestion;

import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;

import java.net.URI;

/**
 * Ingestion adapter for one Brazilian state's NFC-e public consultation
 * (M003 §3). A provider's only job is
 * <em>external fiscal representation → structured fiscal representation</em>: it
 * turns a QR code URL into a {@link FiscalDocumentRequest}. It never persists,
 * and never touches Catalog / Resolution / Merchant concerns.
 *
 * <p>New states are added by registering another implementation; the Fiscal
 * persistence model and {@code NfceImportService} do not change. Only
 * {@code SaoPauloNfceProvider} exists today.
 */
public interface NfceProvider {

    /**
     * Cheap, non-throwing check: does this provider handle the given QR code URL
     * (by host / path)? Used to route an import request to exactly one provider.
     */
    boolean supports(URI qrCodeUrl);

    /**
     * Extracts and validates the 44-digit NFC-e access key from the QR code URL
     * without any network call. Used to short-circuit an already-imported
     * document before contacting SEFAZ.
     *
     * @throws InvalidQrCodeUrlException the URL is not a valid consultation URL for this provider
     * @throws InvalidQrPayloadException the QR payload carries no valid access key
     */
    String peekAccessKey(URI qrCodeUrl);

    /**
     * Fetches the public consultation for {@code qrCodeUrl} and parses it into a
     * {@link FiscalDocumentRequest}. Does not persist.
     *
     * @throws InvalidQrCodeUrlException / {@link InvalidQrPayloadException} the URL/payload is invalid
     * @throws SefazUnavailableException the consultation service could not be reached
     * @throws SefazUnexpectedResponseException the consultation returned something unusable
     * @throws NfceParseException the consultation page could not be parsed
     */
    FiscalDocumentRequest fetchAndParse(URI qrCodeUrl);
}
