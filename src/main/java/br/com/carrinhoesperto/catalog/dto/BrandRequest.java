package br.com.carrinhoesperto.catalog.dto;

import java.util.UUID;

/**
 * Body of {@code POST /api/catalog/brands} (M004).
 *
 * @param manufacturerId optional owning manufacturer; {@code null} is allowed
 */
public record BrandRequest(String name, UUID manufacturerId) {
}
