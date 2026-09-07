package br.com.carrinhoesperto.fiscal.ingestion;

/**
 * The São Paulo SEFAZ consultation responded, but not in a way we can use: a
 * redirect, a non-200/non-5xx status, a wrong content type, or a body over the
 * size limit. Maps to HTTP 502.
 *
 * <p>The constructor reason is for server-side logging only; it is never sent
 * to the client.
 */
public class SefazUnexpectedResponseException extends RuntimeException {

    public SefazUnexpectedResponseException(String reason) {
        super(reason);
    }
}
