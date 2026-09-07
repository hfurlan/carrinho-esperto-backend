package br.com.carrinhoesperto.resolution;

/**
 * The kind of datum an item of {@link ResolutionEvidence} records (M005 §9,
 * §29). M005 records only {@link #GTIN}; the enum is extensible for later
 * evidence-based resolution.
 */
public enum ResolutionEvidenceType {
    GTIN
}
