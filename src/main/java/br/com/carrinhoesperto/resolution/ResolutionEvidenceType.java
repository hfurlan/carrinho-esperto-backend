package br.com.carrinhoesperto.resolution;

/**
 * The kind of datum an item of {@link ResolutionEvidence} records (M005 §9,
 * §29). M005 records {@link #GTIN}; M007 adds {@link #STORE_PRODUCT_CODE}. The
 * enum stays extensible for later evidence-based resolution.
 */
public enum ResolutionEvidenceType {

    /** The 8..14-digit GTIN taken from the fiscal item's cEAN / cEANTrib. */
    GTIN,

    /**
     * The store product code ({@code cProd}) from the fiscal item, resolved
     * against a curated {@code (store, cProd) -> Product} mapping (M007). The
     * evidence {@code value} is the {@code cProd}; {@code metadata} is the
     * issuing store's CNPJ.
     */
    STORE_PRODUCT_CODE
}
