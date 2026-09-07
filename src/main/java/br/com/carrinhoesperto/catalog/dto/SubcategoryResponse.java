package br.com.carrinhoesperto.catalog.dto;

import br.com.carrinhoesperto.catalog.Subcategory;

import java.time.Instant;
import java.util.UUID;

/** Client representation of a {@link Subcategory}. */
public record SubcategoryResponse(
    UUID id, UUID categoryId, String name, Instant createdAt, Instant updatedAt) {

    public static SubcategoryResponse from(Subcategory subcategory) {
        return new SubcategoryResponse(
            subcategory.id(), subcategory.categoryId(), subcategory.name(),
            subcategory.createdAt(), subcategory.updatedAt());
    }
}
