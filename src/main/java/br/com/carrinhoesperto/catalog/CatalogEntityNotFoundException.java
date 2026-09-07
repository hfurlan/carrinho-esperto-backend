package br.com.carrinhoesperto.catalog;

import java.util.UUID;

/**
 * A catalog entity referenced by id — directly (a lookup) or as a foreign key
 * on a create payload — does not exist. Maps to HTTP 404.
 */
public class CatalogEntityNotFoundException extends RuntimeException {

    public CatalogEntityNotFoundException(String entity, UUID id) {
        super(entity + " not found: " + id);
    }
}
