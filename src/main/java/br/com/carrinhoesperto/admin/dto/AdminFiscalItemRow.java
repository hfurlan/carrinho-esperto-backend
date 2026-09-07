package br.com.carrinhoesperto.admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row of {@code GET /api/admin/fiscal-items} (M006). {@code rawDescription}
 * is the fiscal text verbatim; {@code resolutionStatus} / {@code canonicalProductId}
 * are {@code null} when the item has not been resolved.
 */
public record AdminFiscalItemRow(
    UUID id,
    UUID fiscalDocumentId,
    String accessKey,
    String rawDescription,
    String supplierItemCode,
    String gtin,
    String gtinTaxable,
    BigDecimal quantity,
    String unit,
    BigDecimal unitPrice,
    BigDecimal totalPrice,
    String resolutionStatus,
    UUID canonicalProductId
) {
}
