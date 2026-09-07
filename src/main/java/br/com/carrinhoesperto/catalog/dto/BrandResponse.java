package br.com.carrinhoesperto.catalog.dto;

import br.com.carrinhoesperto.catalog.Brand;

import java.time.Instant;
import java.util.UUID;

/** Client representation of a {@link Brand}. */
public record BrandResponse(
    UUID id, String name, UUID manufacturerId, Instant createdAt, Instant updatedAt) {

    public static BrandResponse from(Brand brand) {
        return new BrandResponse(
            brand.id(), brand.name(), brand.manufacturerId(), brand.createdAt(), brand.updatedAt());
    }
}
