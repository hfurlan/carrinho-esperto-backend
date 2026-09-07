package br.com.carrinhoesperto.catalog.dto;

import br.com.carrinhoesperto.catalog.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Client representation of a canonical {@link Product}. Platform-owned data —
 * the same product is returned to every user, and there is no owner field.
 */
public record ProductResponse(
    UUID id,
    String canonicalName,
    UUID brandId,
    UUID manufacturerId,
    UUID categoryId,
    UUID subcategoryId,
    UUID productFamilyId,
    String gtin,
    BigDecimal packageQuantity,
    String packageUnit,
    String packagingType,
    Instant createdAt,
    Instant updatedAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.id(),
            product.canonicalName(),
            product.brandId(),
            product.manufacturerId(),
            product.categoryId(),
            product.subcategoryId(),
            product.productFamilyId(),
            product.gtin(),
            product.packageQuantity(),
            product.packageUnit() == null ? null : product.packageUnit().name(),
            product.packagingType() == null ? null : product.packagingType().name(),
            product.createdAt(),
            product.updatedAt());
    }
}
