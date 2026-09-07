package br.com.carrinhoesperto.dashboard.dto;

import java.util.List;

/**
 * {@code GET /api/dashboard/spending} — small purpose-built aggregates so the
 * frontend never reconstructs them from raw rows.
 *
 * @param byMonth  spending per calendar month, oldest first
 * @param byIssuer spending per supermarket, biggest first
 */
public record DashboardSpendingResponse(
    List<MonthlySpendingResponse> byMonth,
    List<IssuerSpendingResponse> byIssuer
) {
}
