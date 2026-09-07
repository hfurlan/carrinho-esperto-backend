package br.com.carrinhoesperto.fiscal.ingestion;

/**
 * The QR code URL is a syntactically valid http(s) URL, but no registered
 * {@link NfceProvider} recognises it — i.e. it is not a São Paulo NFC-e
 * consultation URL. Other Brazilian states are not supported yet. Maps to
 * HTTP 422.
 */
public class UnsupportedNfceProviderException extends RuntimeException {

    public UnsupportedNfceProviderException() {
        super("Only São Paulo (SP) NFC-e consultation URLs are supported");
    }
}
