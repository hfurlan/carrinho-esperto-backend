package br.com.carrinhoesperto.resolution;

import java.util.UUID;

/**
 * A deterministic hit from a {@link ResolutionStrategy}: the canonical product,
 * the method that found it, and the single piece of evidence that explains it.
 * The absence of a match is an empty {@code Optional}, not a {@code StrategyMatch}
 * with nulls.
 */
public record StrategyMatch(
    UUID canonicalProductId,
    ResolutionMethod method,
    EvidenceRecord evidence
) {
}
