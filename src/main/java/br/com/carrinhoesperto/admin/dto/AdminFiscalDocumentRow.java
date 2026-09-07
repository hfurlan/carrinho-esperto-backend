package br.com.carrinhoesperto.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of {@code GET /api/admin/fiscal-documents} (M006). */
public record AdminFiscalDocumentRow(
    UUID id,
    UUID ownerUserId,
    String accessKey,
    Instant issuedAt,
    String issuerCnpj,
    String issuerName,
    BigDecimal totalAmount,
    long itemCount,
    Instant createdAt
) {
}
