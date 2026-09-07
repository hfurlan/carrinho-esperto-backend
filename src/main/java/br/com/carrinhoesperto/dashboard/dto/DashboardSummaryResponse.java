package br.com.carrinhoesperto.dashboard.dto;

import java.math.BigDecimal;

/**
 * {@code GET /api/dashboard/summary} — the caller's own headline numbers.
 *
 * @param totalSpent           sum of every imported receipt's total ({@code vNF})
 * @param receiptCount         number of imported fiscal documents
 * @param itemCount            number of fiscal items across those documents
 * @param resolvedItemCount    items linked to a canonical product (M005 GTIN resolution)
 * @param unresolvedItemCount  items not yet linked ({@code itemCount - resolvedItemCount})
 */
public record DashboardSummaryResponse(
    BigDecimal totalSpent,
    long receiptCount,
    long itemCount,
    long resolvedItemCount,
    long unresolvedItemCount
) {
}
