package br.com.carrinhoesperto.resolution;

/**
 * Where an item of {@link ResolutionEvidence} came from (M005 §9, §29). M005
 * records only {@link #FISCAL_ITEM}; the enum is extensible (a future store
 * vocabulary, an external provider, a user confirmation, …).
 */
public enum ResolutionEvidenceSource {
    FISCAL_ITEM
}
