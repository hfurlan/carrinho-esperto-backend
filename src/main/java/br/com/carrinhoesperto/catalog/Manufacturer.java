package br.com.carrinhoesperto.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * The company that manufactures a product. Platform-owned canonical data — not
 * user-scoped. Distinct from {@link Brand} (M004 §29): a manufacturer may own
 * several brands, and a brand's manufacturer may be unknown.
 */
public record Manufacturer(UUID id, String name, Instant createdAt, Instant updatedAt) {
}
