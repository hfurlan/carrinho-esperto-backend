package br.com.carrinhoesperto.fiscal.ingestion;

/**
 * The QR code URL is a valid São Paulo NFC-e consultation URL, but its {@code p}
 * parameter is missing, unreadable, or does not carry a 44-digit NFC-e access
 * key. Maps to HTTP 400.
 */
public class InvalidQrPayloadException extends RuntimeException {

    public InvalidQrPayloadException() {
        super("The QR code payload does not contain a valid 44-digit NFC-e access key");
    }
}
