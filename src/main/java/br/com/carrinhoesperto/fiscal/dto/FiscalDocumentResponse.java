package br.com.carrinhoesperto.fiscal.dto;

import br.com.carrinhoesperto.fiscal.FiscalDocument;
import br.com.carrinhoesperto.fiscal.FiscalItem;
import br.com.carrinhoesperto.fiscal.FiscalPayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full payload of {@code GET /api/fiscal-documents/{id}} (M002), and the body
 * returned by {@code POST /api/fiscal-documents} so the caller never needs a
 * second round trip. Items are ordered by {@code lineNumber}, payments by their
 * position in the document.
 */
public record FiscalDocumentResponse(
    UUID id,
    String accessKey,
    Long documentNumber,
    Integer series,
    Instant issuedAt,
    FiscalIssuerResponse issuer,
    BigDecimal totalAmount,
    BigDecimal totalItemsAmount,
    BigDecimal totalDiscount,
    Instant createdAt,
    List<FiscalItemResponse> items,
    List<FiscalPaymentResponse> payments
) {

    public static FiscalDocumentResponse from(FiscalDocument document,
                                              List<FiscalItem> items,
                                              List<FiscalPayment> payments) {
        return new FiscalDocumentResponse(
            document.id(),
            document.accessKey(),
            document.documentNumber(),
            document.series(),
            document.issuedAt(),
            FiscalIssuerResponse.from(document.issuer()),
            document.totalAmount(),
            document.totalItemsAmount(),
            document.totalDiscount(),
            document.createdAt(),
            items.stream().map(FiscalItemResponse::from).toList(),
            payments.stream().map(FiscalPaymentResponse::from).toList());
    }
}
