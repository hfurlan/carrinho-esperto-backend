package br.com.carrinhoesperto.resolution;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Deterministic store-product-code resolution (M007). If the fiscal item carries
 * a {@code cProd} and the receipt's issuing store has a curated
 * {@code (storeId, cProd) -> Product} mapping, resolve to that product.
 *
 * <p>Runs after {@link GtinExactResolutionStrategy}: a valid exact GTIN match
 * always wins (M007 §7). No fuzzy matching — the {@code cProd} is compared
 * verbatim (only trimmed) against the stored code. Mappings are per store; the
 * same {@code cProd} in another store is a different mapping (M007 §14).
 */
@Component
public class StoreProductCodeResolutionStrategy implements ResolutionStrategy {

    private final StoreProductCodeRepository storeProductCodeRepository;

    StoreProductCodeResolutionStrategy(StoreProductCodeRepository storeProductCodeRepository) {
        this.storeProductCodeRepository = storeProductCodeRepository;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Optional<StrategyMatch> match(FiscalItemContext ctx) {
        String storeId = ctx.storeId();
        String code = ctx.supplierItemCode();
        if (storeId == null || storeId.isBlank() || code == null || code.isBlank()) {
            return Optional.empty();
        }
        String trimmedCode = code.strip();
        return storeProductCodeRepository.findByStoreAndCode(storeId, trimmedCode)
            .map(mapping -> new StrategyMatch(
                mapping.canonicalProductId(),
                ResolutionMethod.STORE_PRODUCT_CODE,
                new EvidenceRecord(
                    ResolutionEvidenceType.STORE_PRODUCT_CODE,
                    ResolutionEvidenceSource.FISCAL_ITEM,
                    trimmedCode,
                    storeId)));
    }
}
