package br.com.carrinhoesperto.catalog;

/**
 * The canonical GTIN on a product create payload is already assigned to another
 * canonical product. A canonical GTIN identifies at most one product
 * (M004 §8). Maps to HTTP 409.
 */
public class GtinAlreadyAssignedException extends RuntimeException {

    public GtinAlreadyAssignedException(String gtin) {
        super("GTIN already assigned to another product: " + gtin);
    }
}
