package br.com.carrinhoesperto.catalog.dto;

import br.com.carrinhoesperto.catalog.Manufacturer;

import java.time.Instant;
import java.util.UUID;

/** Client representation of a {@link Manufacturer}. */
public record ManufacturerResponse(UUID id, String name, Instant createdAt, Instant updatedAt) {

    public static ManufacturerResponse from(Manufacturer manufacturer) {
        return new ManufacturerResponse(
            manufacturer.id(), manufacturer.name(), manufacturer.createdAt(), manufacturer.updatedAt());
    }
}
