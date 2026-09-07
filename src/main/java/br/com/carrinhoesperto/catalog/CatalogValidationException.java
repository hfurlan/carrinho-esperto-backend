package br.com.carrinhoesperto.catalog;

/**
 * A catalog create/update payload is not acceptable: a blank name, a duplicate
 * reference name, a malformed GTIN, an invalid package quantity, or an unknown
 * package unit / packaging type. Maps to HTTP 400.
 */
public class CatalogValidationException extends RuntimeException {

    public CatalogValidationException(String message) {
        super(message);
    }
}
