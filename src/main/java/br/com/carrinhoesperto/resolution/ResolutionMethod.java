package br.com.carrinhoesperto.resolution;

/**
 * How a resolution reached its conclusion (M005 §8). Extensible without
 * changing the {@code ProductResolution} shape.
 *
 * <p>M005 implements {@link #GTIN_EXACT}; M007 adds {@link #STORE_PRODUCT_CODE}.
 * Future milestones may add {@code STORE_ALIAS}, {@code GLOBAL_ALIAS},
 * {@code CATALOG_MATCH}, {@code EXTERNAL_PROVIDER}, {@code EVIDENCE_RANKING},
 * {@code AI_ASSISTED}, {@code USER_CONFIRMED} — none of which exist yet.
 *
 * <p>Precedence (highest first): {@code GTIN_EXACT}, then
 * {@code STORE_PRODUCT_CODE}. GTIN always wins.
 */
public enum ResolutionMethod {

    /**
     * The fiscal item carried a usable GTIN (cEAN, else cEANTrib) that exactly
     * matched the canonical GTIN of exactly one catalog product. Deterministic —
     * there is no probabilistic score attached.
     */
    GTIN_EXACT,

    /**
     * The fiscal item's store product code ({@code cProd}) had a curated
     * {@code (store, cProd) -> Product} mapping for the receipt's issuing store
     * (M007). Deterministic; the store is identified by issuer CNPJ.
     */
    STORE_PRODUCT_CODE
}
