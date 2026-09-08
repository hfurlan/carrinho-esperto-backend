package br.com.carrinhoesperto.resolution;

import java.util.UUID;

/** No {@link StoreProductCode} with the given id (M007). Mapped to {@code 404}. */
public class StoreProductCodeNotFoundException extends RuntimeException {

    public StoreProductCodeNotFoundException(UUID id) {
        super("Store product code not found: " + id);
    }
}
