package br.com.carrinhoesperto.fiscal.dto;

import br.com.carrinhoesperto.fiscal.FiscalItem;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Client representation of a {@link FiscalItem}. Every field is returned exactly
 * as it was stored — {@code rawDescription} is the original fiscal text and
 * {@code gtin}/{@code gtinTaxable} keep the literal {@code "SEM GTIN"} when that
 * is what the document reported.
 */
public record FiscalItemResponse(
    UUID id,
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

    public static FiscalItemResponse from(FiscalItem item) {
        return new FiscalItemResponse(
            item.id(),
            item.lineNumber(),
            item.rawDescription(),
            item.gtin(),
            item.gtinTaxable(),
            item.supplierItemCode(),
            item.quantity(),
            item.unit(),
            item.unitPrice(),
            item.totalPrice(),
            item.discount(),
            item.ncm(),
            item.cest());
    }
}
