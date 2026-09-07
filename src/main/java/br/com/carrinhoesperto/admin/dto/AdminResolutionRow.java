package br.com.carrinhoesperto.admin.dto;

import java.time.Instant;
import java.util.UUID;

/** One row of {@code GET /api/admin/resolutions} (M006) — diagnostic view. */
public record AdminResolutionRow(
    UUID id,
    UUID fiscalItemId,
    String fiscalRawDescription,
    String status,
    String method,
    UUID canonicalProductId,
    String canonicalProductName,
    long evidenceCount,
    Instant createdAt,
    Instant updatedAt
) {
}
