package br.com.carrinhoesperto.resolution;

import java.util.UUID;

/**
 * No fiscal item with the given id is owned by the authenticated caller — it
 * does not exist, or it belongs to another user (the two are indistinguishable
 * so cross-user fiscal data is not leaked, M005 §16). Maps to HTTP 404.
 */
public class FiscalItemNotFoundException extends RuntimeException {

    public FiscalItemNotFoundException(UUID id) {
        super("Fiscal item not found: " + id);
    }
}
