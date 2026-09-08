package br.com.carrinhoesperto.resolution;

import java.time.Instant;
import java.util.UUID;

/**
 * A curated mapping from a supermarket's own product code to a canonical catalog
 * product (M007). Resolution-owned, <strong>platform-owned</strong> (no user
 * column): one mapping benefits every user importing from that store.
 *
 * @param storeId            issuing store's CNPJ, digits only (Fiscal issuer identity)
 * @param code               the store's {@code cProd}, verbatim — the identity signal,
 *                           <em>not</em> the fiscal raw description
 * @param canonicalProductId the Catalog product this code refers to
 */
public record StoreProductCode(
    UUID id,
    String storeId,
    String code,
    UUID canonicalProductId,
    Instant createdAt,
    Instant updatedAt
) {
}
