package br.com.carrinhoesperto.fiscal;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One printed line of a {@link FiscalDocument}. Every value is fiscal truth,
 * stored and returned exactly as reported.
 *
 * <p>{@code rawDescription} ({@code xProd}) is never replaced with a canonical
 * product name (docs/architecture.md §4.1). {@code gtin} / {@code gtinTaxable}
 * keep the literal {@code "SEM GTIN"} when the document says so — that fact is
 * not thrown away by turning it into {@code null}. {@code supplierItemCode}
 * ({@code cProd}) is the issuer's own product code, preserved for future
 * store-specific resolution (docs/resolution-model.md §7).
 *
 * @param id               surrogate identifier
 * @param fiscalDocumentId owning document
 * @param lineNumber       {@code nItem} — 1-based position within the document
 * @param rawDescription   {@code xProd}, verbatim
 * @param gtin             {@code cEAN}: a GTIN, the literal {@code "SEM GTIN"}, or {@code null} if absent
 * @param gtinTaxable      {@code cEANTrib}, same convention as {@code gtin}
 * @param supplierItemCode {@code cProd}, or {@code null} when not reported
 * @param quantity         {@code qCom}
 * @param unit             {@code uCom}
 * @param unitPrice        {@code vUnCom}
 * @param totalPrice       {@code vProd} — the line total as reported
 * @param discount         {@code vDesc} for the line, or {@code null}
 * @param ncm              NCM classification, or {@code null}
 * @param cest             CEST classification, or {@code null}
 */
public record FiscalItem(
    UUID id,
    UUID fiscalDocumentId,
    int lineNumber,
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
