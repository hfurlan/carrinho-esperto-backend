package br.com.carrinhoesperto.fiscal.dto;

import java.math.BigDecimal;

/**
 * One line of {@link FiscalDocumentRequest}.
 *
 * <p>{@code rawDescription} is stored exactly as sent — it is fiscal truth and
 * is never canonicalized. Pass the literal {@code "SEM GTIN"} in {@code gtin} /
 * {@code gtinTaxable} when the document reports no GTIN; that string is
 * preserved rather than collapsed to {@code null}. {@code lineNumber} is
 * optional — when every item omits it, positions are assigned 1..n in payload
 * order; when supplied it must be a positive, document-unique {@code nItem}.
 *
 * @param quantity   {@code qCom}; required, {@code >= 0}
 * @param unit       {@code uCom}; required
 * @param unitPrice  {@code vUnCom}; required, {@code >= 0}
 * @param totalPrice {@code vProd}; required, {@code >= 0}
 * @param discount   {@code vDesc} for the line; optional, {@code >= 0}
 */
public record FiscalItemRequest(
    Integer lineNumber,
    String rawDescription,
    String gtin,
    String gtinTaxable,
    String supplierItemCode,
    BigDecimal quantity,
    String unit,
    BigDecimal unitPrice,
    BigDecimal totalPrice,
    BigDecimal discount,
    String ncm,
    String cest
) {
}
