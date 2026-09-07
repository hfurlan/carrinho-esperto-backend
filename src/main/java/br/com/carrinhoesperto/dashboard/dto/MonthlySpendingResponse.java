package br.com.carrinhoesperto.dashboard.dto;

import java.math.BigDecimal;

/**
 * One calendar month of the caller's spending.
 *
 * @param month        {@code YYYY-MM} (issue date, America/São_Paulo)
 * @param total        summed receipt total for the month
 * @param receiptCount receipts imported with an issue date in the month
 */
public record MonthlySpendingResponse(String month, BigDecimal total, long receiptCount) {
}
