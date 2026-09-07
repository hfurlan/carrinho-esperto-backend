package br.com.carrinhoesperto.fiscal;

import java.util.UUID;

/**
 * No fiscal document with the given id is owned by the current user. The same
 * response is used whether the document does not exist or belongs to someone
 * else, so ownership is not leaked (docs/fiscal-core.md §12). Maps to HTTP 404.
 */
public class FiscalDocumentNotFoundException extends RuntimeException {

    public FiscalDocumentNotFoundException(UUID id) {
        super("Fiscal document not found: " + id);
    }
}
