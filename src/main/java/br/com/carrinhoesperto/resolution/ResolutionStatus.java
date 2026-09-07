package br.com.carrinhoesperto.resolution;

/**
 * Outcome of interpreting a fiscal item against the canonical catalog
 * (M005 §6, §31).
 *
 * <ul>
 *   <li>{@link #RESOLVED} — exactly one canonical product identified from
 *       deterministic evidence.</li>
 *   <li>{@link #PROBABLE} — a leading candidate that available evidence strongly
 *       suggests, but not deterministically. <em>Not produced in M005.</em></li>
 *   <li>{@link #AMBIGUOUS} — several plausible canonical products remain, no
 *       choice forced. <em>Not produced in M005.</em></li>
 *   <li>{@link #UNRESOLVED} — the evidence was evaluated and no sufficiently
 *       reliable canonical product was identified. A valid domain outcome, never
 *       an error.</li>
 * </ul>
 *
 * <p>M005's deterministic GTIN resolver produces only {@code RESOLVED} or
 * {@code UNRESOLVED}. The full vocabulary is defined now for the evidence-based
 * resolution of later milestones.
 */
public enum ResolutionStatus {
    RESOLVED,
    PROBABLE,
    AMBIGUOUS,
    UNRESOLVED
}
