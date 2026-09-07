package br.com.carrinhoesperto.catalog.dto;

import br.com.carrinhoesperto.catalog.ProductFamily;

import java.time.Instant;
import java.util.UUID;

/** Client representation of a {@link ProductFamily}. */
public record ProductFamilyResponse(
    UUID id, UUID brandId, String name, Instant createdAt, Instant updatedAt) {

    public static ProductFamilyResponse from(ProductFamily family) {
        return new ProductFamilyResponse(
            family.id(), family.brandId(), family.name(), family.createdAt(), family.updatedAt());
    }
}
