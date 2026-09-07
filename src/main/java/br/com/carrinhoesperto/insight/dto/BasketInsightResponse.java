package br.com.carrinhoesperto.insight.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Basket insight for one fiscal document (M006.1, docs/basket-insight.md): the
 * receipt grouped into categories plus a qualitative health seal. Derived on read
 * from fiscal truth; nothing here is persisted.
 *
 * @param documentId        the fiscal document this describes
 * @param seal              stable enum name ({@code EQUILIBRADO} / {@code ATENCAO} /
 *                          {@code MUITOS_SUPERFLUOS} / {@code SEM_DADOS})
 * @param sealLabel         pt-BR display label for the seal
 * @param categories        per-bucket breakdown, biggest value share first,
 *                          empty buckets omitted
 * @param superfluousShare  value fraction in supérfluo buckets (doces + bebidas
 *                          de baixo valor), 0..1
 * @param basicShare        value fraction in "básicos in natura", 0..1
 * @param unclassifiedShare value fraction the ruleset could not place, 0..1
 * @param messages          1–2 short pt-BR sentences explaining the seal
 */
public record BasketInsightResponse(
    UUID documentId,
    String seal,
    String sealLabel,
    List<CategoryBreakdown> categories,
    BigDecimal superfluousShare,
    BigDecimal basicShare,
    BigDecimal unclassifiedShare,
    List<String> messages
) {
}
