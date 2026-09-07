package br.com.carrinhoesperto.fiscal.ingestion;

/**
 * The São Paulo SEFAZ consultation page was retrieved but its fiscal content
 * could not be extracted — an expected section, item field, total or the
 * emission date/time was missing or unreadable. Maps to HTTP 502.
 *
 * <p>The constructor reason is a short internal tag for logging (e.g.
 * {@code "items-table"}); it never contains page text and is not sent to the
 * client.
 */
public class NfceParseException extends RuntimeException {

    public NfceParseException(String reason) {
        super(reason);
    }
}
