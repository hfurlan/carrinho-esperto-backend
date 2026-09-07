package br.com.carrinhoesperto.session.dto;

/**
 * Outcome of {@code POST /api/anonymous-sessions/claim} (M006.2): how many
 * trial receipts were moved into the now-authenticated account. {@code 0} when
 * there was nothing to claim.
 */
public record ClaimResult(int movedReceipts) {
}
