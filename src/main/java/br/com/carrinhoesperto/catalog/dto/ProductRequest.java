package br.com.carrinhoesperto.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of {@code POST /api/catalog/products} (M004).
 *
 * <p>Contains only <em>canonical</em> information. It must NOT carry fiscal or
 * resolution concepts (rawDescription, cProd, fiscalDocumentId, userId,
 * confidence, resolutionMethod) — those belong to other bounded contexts
 * (M004 §23).
 *
 * @param canonicalName   required, human-readable; stored verbatim
 * @param brandId         optional FK
 * @param manufacturerId  optional FK
 * @param categoryId      optional FK
 * @param subcategoryId   optional FK
 * @param productFamilyId optional FK
 * @param gtin            optional canonical GTIN, 8–14 digits; never "SEM GTIN"
 * @param packageQuantity optional pack size, {@code > 0}
 * @param packageUnit     optional, one of {@code G|KG|ML|L|UN} (case-insensitive)
 * @param packagingType   optional, one of the {@code PackagingType} values (case-insensitive)
 */
public record ProductRequest(
    String canonicalName,
    UUID brandId,
    UUID manufacturerId,
    UUID categoryId,
    UUID subcategoryId,
    UUID productFamilyId,
    String gtin,
    BigDecimal packageQuantity,
    String packageUnit,
    String packagingType
) {
}
