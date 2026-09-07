package br.com.carrinhoesperto.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A <strong>canonical product identity</strong> known by Carrinho Esperto
 * (M004 §6) — what a product <em>is</em>, independent of any supermarket's
 * fiscal description.
 *
 * <p>Platform-owned and shared: there is no owner/user field. Every
 * relationship is optional — the catalog is built incrementally and a bare
 * {@code canonicalName} is a valid start.
 *
 * <p>Contains no Resolution concepts (confidence, evidence, method) and no
 * Fiscal concepts (rawDescription, cProd, fiscalDocumentId). {@code gtin} is the
 * <em>canonical</em> GTIN, distinct from the observed GTIN on a fiscal item.
 *
 * @param canonicalName   human-readable canonical name, kept verbatim (not lower-cased / de-accented)
 * @param gtin            canonical GTIN (8–14 digits) or {@code null}; never the "SEM GTIN" literal
 * @param packageQuantity pack size (e.g. {@code 1}, {@code 395}, {@code 12}) or {@code null}
 * @param packageUnit     {@link PackageUnit} or {@code null}
 * @param packagingType   {@link PackagingType} or {@code null}
 */
public record Product(
    UUID id,
    String canonicalName,
    UUID brandId,
    UUID manufacturerId,
    UUID categoryId,
    UUID subcategoryId,
    UUID productFamilyId,
    String gtin,
    BigDecimal packageQuantity,
    PackageUnit packageUnit,
    PackagingType packagingType,
    Instant createdAt,
    Instant updatedAt
) {
}
