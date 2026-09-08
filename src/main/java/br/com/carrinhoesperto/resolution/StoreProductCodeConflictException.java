package br.com.carrinhoesperto.resolution;

/**
 * A {@code (store, cProd)} already maps to a canonical product and the caller
 * tried to create a different mapping for it (M007 §13). Mapped to {@code 409}.
 * Deterministic ambiguity is surfaced, never silently resolved.
 */
public class StoreProductCodeConflictException extends RuntimeException {

    public StoreProductCodeConflictException(String storeId, String code, java.util.UUID existingProductId) {
        super("Store " + storeId + " code " + code + " is already mapped to product " + existingProductId
            + ". Update or remove the existing mapping instead.");
    }
}
