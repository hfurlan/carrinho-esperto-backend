package br.com.carrinhoesperto.catalog.dto;

import java.util.UUID;

/**
 * Body of {@code POST /api/catalog/subcategories} (M004).
 *
 * @param categoryId owning category — required
 */
public record SubcategoryRequest(UUID categoryId, String name) {
}
