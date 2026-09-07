package br.com.carrinhoesperto.admin.dto;

/**
 * {@code GET /api/admin/dashboard} — platform-wide operational counts (M006 §10).
 * No personal data.
 */
public record AdminDashboardResponse(
    long fiscalDocuments,
    long fiscalItems,
    long resolvedItems,
    long unresolvedItems,
    long catalogProducts,
    long users
) {
}
