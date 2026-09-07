package br.com.carrinhoesperto.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * Second (and, in M004, last) taxonomy level under a {@link Category}
 * (e.g. "Leites" under "Laticínios").
 *
 * @param categoryId owning category — required
 */
public record Subcategory(UUID id, UUID categoryId, String name, Instant createdAt, Instant updatedAt) {
}
