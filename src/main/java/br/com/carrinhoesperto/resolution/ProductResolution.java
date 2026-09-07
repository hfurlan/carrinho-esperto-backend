package br.com.carrinhoesperto.resolution;

import java.time.Instant;
import java.util.UUID;

/**
 * The interpretation connecting one user-owned {@code FiscalItem} to a shared,
 * platform-owned catalog {@code Product} (M005 §5).
 *
 * <p>Resolution is its own bounded context: this record holds only the
 * <em>ids</em> of the fiscal item and the canonical product it links, never
 * copies of those aggregates. There is no numeric confidence field — a
 * deterministic exact GTIN match is represented by
 * {@code status = RESOLVED, method = GTIN_EXACT}, not by a probability.
 *
 * @param fiscalItemId        the interpreted fiscal item (Fiscal context)
 * @param canonicalProductId  the canonical product, or {@code null} when {@code UNRESOLVED}
 * @param method              {@link ResolutionMethod}, or {@code null} when {@code UNRESOLVED}
 */
public record ProductResolution(
    UUID id,
    UUID fiscalItemId,
    UUID canonicalProductId,
    ResolutionStatus status,
    ResolutionMethod method,
    Instant createdAt,
    Instant updatedAt
) {
}
