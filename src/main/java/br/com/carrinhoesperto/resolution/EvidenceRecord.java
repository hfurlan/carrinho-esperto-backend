package br.com.carrinhoesperto.resolution;

/**
 * The one auditable fact a {@link ResolutionStrategy} produces for a match
 * (M005 §9, M007 §19). Mapped 1:1 to a {@link ResolutionEvidence} row.
 *
 * @param type     what kind of datum (GTIN, STORE_PRODUCT_CODE)
 * @param source   where it came from (FISCAL_ITEM)
 * @param value    the datum itself — the GTIN, or the {@code cProd}
 * @param metadata optional context — the store CNPJ for STORE_PRODUCT_CODE, else {@code null}
 */
public record EvidenceRecord(
    ResolutionEvidenceType type,
    ResolutionEvidenceSource source,
    String value,
    String metadata
) {
}
