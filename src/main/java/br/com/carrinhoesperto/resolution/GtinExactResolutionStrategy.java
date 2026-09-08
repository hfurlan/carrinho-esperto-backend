package br.com.carrinhoesperto.resolution;

import br.com.carrinhoesperto.catalog.CatalogService;
import br.com.carrinhoesperto.catalog.dto.ProductResponse;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Deterministic exact-GTIN resolution (M005 §18). Uses {@code cEAN} when it is a
 * usable canonical GTIN, otherwise falls back to {@code cEANTrib}. A {@code cEAN}
 * that is a valid GTIN but matches no catalog product is still "used" — the
 * outcome is {@code UNRESOLVED}, with no fall-through to {@code cEANTrib}.
 * {@code "SEM GTIN"}, blank and non-numeric values are not usable.
 *
 * <p>Highest precedence — GTIN always beats a store product code (M007 §7).
 */
@Component
public class GtinExactResolutionStrategy implements ResolutionStrategy {

    private final CatalogService catalogService;

    GtinExactResolutionStrategy(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Optional<StrategyMatch> match(FiscalItemContext ctx) {
        return selectGtin(ctx).flatMap(gtin ->
            catalogService.findProductByGtin(gtin)
                .map(ProductResponse::id)
                .map(productId -> new StrategyMatch(
                    productId,
                    ResolutionMethod.GTIN_EXACT,
                    new EvidenceRecord(ResolutionEvidenceType.GTIN, ResolutionEvidenceSource.FISCAL_ITEM, gtin, null))));
    }

    private static Optional<String> selectGtin(FiscalItemContext ctx) {
        Optional<String> primary = Gtin.canonical(ctx.gtin());
        return primary.isPresent() ? primary : Gtin.canonical(ctx.gtinTaxable());
    }
}
