package br.com.carrinhoesperto.catalog.dto;

import java.util.UUID;

/**
 * Body of {@code POST /api/catalog/product-families} (M004).
 *
 * @param brandId optional owning brand; {@code null} is allowed
 */
public record ProductFamilyRequest(UUID brandId, String name) {
}
