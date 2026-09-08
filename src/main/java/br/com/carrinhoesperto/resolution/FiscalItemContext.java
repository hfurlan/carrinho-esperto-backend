package br.com.carrinhoesperto.resolution;

import br.com.carrinhoesperto.fiscal.FiscalItem;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemResponse;

import java.util.UUID;

/**
 * The minimal read-only view a {@link ResolutionStrategy} needs of a fiscal item
 * plus the store that issued it. Built from either the Fiscal aggregate (the
 * owner-scoped single-item path) or a {@code FiscalItemResponse} (the
 * admin-scoped document path), so both reuse the same strategies.
 *
 * <p>Resolution reads Fiscal fields by value here; it never persists or mutates
 * the Fiscal aggregate.
 *
 * @param storeId the issuing store's CNPJ (digits only), or {@code null} if unknown
 */
public record FiscalItemContext(
    UUID fiscalItemId,
    String gtin,
    String gtinTaxable,
    String supplierItemCode,
    String storeId
) {

    public static FiscalItemContext from(FiscalItem item, String storeId) {
        return new FiscalItemContext(
            item.id(), item.gtin(), item.gtinTaxable(), item.supplierItemCode(), storeId);
    }

    public static FiscalItemContext from(FiscalItemResponse item, String storeId) {
        return new FiscalItemContext(
            item.id(), item.gtin(), item.gtinTaxable(), item.supplierItemCode(), storeId);
    }
}
