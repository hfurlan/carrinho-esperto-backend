package br.com.carrinhoesperto.catalog.dto;

import br.com.carrinhoesperto.catalog.Category;

import java.time.Instant;
import java.util.UUID;

/** Client representation of a {@link Category}. */
public record CategoryResponse(UUID id, String name, Instant createdAt, Instant updatedAt) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
            category.id(), category.name(), category.createdAt(), category.updatedAt());
    }
}
