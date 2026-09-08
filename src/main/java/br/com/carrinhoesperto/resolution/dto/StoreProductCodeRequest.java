package br.com.carrinhoesperto.resolution.dto;

import java.util.UUID;

/**
 * Admin request to create a {@code (store, cProd) -> Product} mapping (M007).
 *
 * @param storeId            issuing store CNPJ (14 digits; punctuation is stripped)
 * @param code               the store's {@code cProd}, verbatim
 * @param canonicalProductId an existing Catalog product
 */
public record StoreProductCodeRequest(String storeId, String code, UUID canonicalProductId) {
}
