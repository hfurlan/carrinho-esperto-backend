package br.com.carrinhoesperto.resolution.dto;

import br.com.carrinhoesperto.resolution.StoreProductCode;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin view of a {@code (store, cProd) -> Product} mapping (M007). Carries the
 * canonical product name so the admin table needs no second lookup.
 */
public record StoreProductCodeResponse(
    UUID id,
    String storeId,
    String code,
    UUID canonicalProductId,
    String canonicalProductName,
    Instant createdAt,
    Instant updatedAt
) {

    public static StoreProductCodeResponse from(StoreProductCode m, String canonicalProductName) {
        return new StoreProductCodeResponse(
            m.id(), m.storeId(), m.code(), m.canonicalProductId(), canonicalProductName,
            m.createdAt(), m.updatedAt());
    }
}
