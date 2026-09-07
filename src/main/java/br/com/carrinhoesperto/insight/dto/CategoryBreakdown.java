package br.com.carrinhoesperto.insight.dto;

import java.math.BigDecimal;

/**
 * One bucket's slice of a receipt (M006.1 basket insight).
 *
 * @param category    stable enum name (e.g. {@code DOCE_SUPERFLUO})
 * @param label       pt-BR display label
 * @param itemCount   number of fiscal item lines in this bucket
 * @param totalValue  summed line totals (BRL)
 * @param valueShare  fraction of the receipt's classified+unclassified value, 0..1
 * @param superfluous whether this bucket counts against the health seal
 */
public record CategoryBreakdown(
    String category,
    String label,
    int itemCount,
    BigDecimal totalValue,
    BigDecimal valueShare,
    boolean superfluous
) {
}
