package br.com.carrinhoesperto.resolution.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-item resolution state for a whole fiscal document (M006) — powers the
 * receipt-detail screen's "we know what this is" / "not identified yet" badges.
 *
 * @param status                {@code RESOLVED} / {@code UNRESOLVED}, or {@code null}
 *                              from the read endpoint when the item was never resolved
 * @param canonicalProductId    the linked catalog product, or {@code null}
 * @param canonicalProductName  its canonical name (enriched from the catalog), or {@code null}
 * @param method                {@code GTIN_EXACT}, or {@code null}
 * @param updatedAt             when the resolution last changed, or {@code null}
 */
public record DocumentItemResolutionResponse(
    UUID fiscalItemId,
    String status,
    UUID canonicalProductId,
    String canonicalProductName,
    String method,
    Instant updatedAt
) {
}
