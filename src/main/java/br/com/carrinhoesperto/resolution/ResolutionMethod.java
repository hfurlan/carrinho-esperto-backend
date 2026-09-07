package br.com.carrinhoesperto.resolution;

/**
 * How a resolution reached its conclusion (M005 §8). Extensible without
 * changing the {@code ProductResolution} shape.
 *
 * <p>M005 implements only {@link #GTIN_EXACT}. Future milestones may add
 * {@code STORE_PRODUCT_CODE}, {@code STORE_ALIAS}, {@code GLOBAL_ALIAS},
 * {@code CATALOG_MATCH}, {@code EXTERNAL_PROVIDER}, {@code EVIDENCE_RANKING},
 * {@code AI_ASSISTED}, {@code USER_CONFIRMED} — none of which exist yet.
 */
public enum ResolutionMethod {

    /**
     * The fiscal item carried a usable GTIN (cEAN, else cEANTrib) that exactly
     * matched the canonical GTIN of exactly one catalog product. Deterministic —
     * there is no probabilistic score attached.
     */
    GTIN_EXACT
}
