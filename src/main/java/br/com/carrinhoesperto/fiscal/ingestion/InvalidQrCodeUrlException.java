package br.com.carrinhoesperto.fiscal.ingestion;

/**
 * The supplied QR code URL is not a well-formed, HTTPS, São Paulo SEFAZ NFC-e
 * public-consultation URL (bad syntax, {@code http://}, wrong host or path, a
 * non-standard port, embedded credentials, or a host that resolves to a
 * non-public address). Maps to HTTP 400.
 *
 * <p>The message is deliberately generic and never echoes the offending URL.
 */
public class InvalidQrCodeUrlException extends RuntimeException {

    public InvalidQrCodeUrlException() {
        super("The QR code URL is not a recognised São Paulo NFC-e consultation URL");
    }
}
