package br.com.carrinhoesperto.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * A grouping of closely related canonical products that form one product line
 * (M004 §5, §28) — e.g. "Leite UHT Integral" for brand Jussara, grouping the
 * 1 L, 1.5 L and zero-lactose {@link Product} variants. A grouping mechanism,
 * not a substitute for {@link Product}.
 *
 * @param brandId owning brand, or {@code null}
 */
public record ProductFamily(UUID id, UUID brandId, String name, Instant createdAt, Instant updatedAt) {
}
