package br.com.carrinhoesperto.resolution;

import java.util.Optional;

/**
 * One deterministic way to identify the canonical product a fiscal item refers
 * to (M007 §18). {@link ProductResolutionService} runs the registered strategies
 * in ascending {@link #order()} and takes the first match — so a lower order is
 * higher precedence.
 *
 * <p>Active strategies after M007: {@link GtinExactResolutionStrategy} (10),
 * {@link StoreProductCodeResolutionStrategy} (20). This is a small ordered list,
 * not a rules engine; future deterministic methods add one class each.
 */
public interface ResolutionStrategy {

    /** Precedence — lower runs first. GTIN is 10, store product code is 20. */
    int order();

    /** The match for this item, or empty when this strategy has nothing to say. */
    Optional<StrategyMatch> match(FiscalItemContext ctx);
}
