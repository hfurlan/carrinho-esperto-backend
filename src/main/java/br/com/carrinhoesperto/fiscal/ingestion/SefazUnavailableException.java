package br.com.carrinhoesperto.fiscal.ingestion;

/**
 * The São Paulo SEFAZ public consultation could not be reached: connection
 * failure, timeout, DNS failure, or an upstream 5xx/429. Maps to HTTP 502.
 *
 * <p>The constructor reason is for server-side logging only; it is never sent
 * to the client.
 */
public class SefazUnavailableException extends RuntimeException {

    public SefazUnavailableException(String reason) {
        super(reason);
    }
}
