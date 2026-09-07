package br.com.carrinhoesperto.fiscal.dto;

import br.com.carrinhoesperto.fiscal.FiscalDocumentRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code GET /api/fiscal-documents} (M002) — enough to render a list
 * without fetching each document's items and payments.
 */
public record FiscalDocumentSummaryResponse(
    UUID id,
    String accessKey,
    Long documentNumber,
    Integer series,
    Instant issuedAt,
    String issuerCnpj,
    String issuerTradeName,
    BigDecimal totalAmount,
    long itemCount,
    Instant createdAt
) {

    public static FiscalDocumentSummaryResponse from(FiscalDocumentRepository.Summary summary) {
        var document = summary.document();
        return new FiscalDocumentSummaryResponse(
            document.id(),
            document.accessKey(),
            document.documentNumber(),
            document.series(),
            document.issuedAt(),
            document.issuer().cnpj(),
            document.issuer().tradeName(),
            document.totalAmount(),
            summary.itemCount(),
            document.createdAt());
    }
}
