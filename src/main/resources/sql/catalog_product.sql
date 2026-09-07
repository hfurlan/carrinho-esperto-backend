-- SQL for ProductRepository (M004), loaded through ELSQL.
--
-- Product is platform-owned canonical data: no owner/user filter. GTIN lookup
-- returns at most one product (unique index). Name search is deterministic and
-- case-insensitive — NO fuzzy matching (that is Resolution, M005+).

@NAME(insert)
  INSERT INTO product (
      id, canonical_name, brand_id, manufacturer_id, category_id, subcategory_id,
      product_family_id, gtin, package_quantity, package_unit, packaging_type)
  VALUES (
      :id, :canonicalName, :brandId, :manufacturerId, :categoryId, :subcategoryId,
      :productFamilyId, :gtin, :packageQuantity, :packageUnit, :packagingType)

@NAME(findById)
  SELECT id, canonical_name, brand_id, manufacturer_id, category_id, subcategory_id,
         product_family_id, gtin, package_quantity, package_unit, packaging_type,
         created_at, updated_at
  FROM product
  WHERE id = :id

@NAME(findByGtin)
  SELECT id, canonical_name, brand_id, manufacturer_id, category_id, subcategory_id,
         product_family_id, gtin, package_quantity, package_unit, packaging_type,
         created_at, updated_at
  FROM product
  WHERE gtin = :gtin

@NAME(searchByName)
  SELECT id, canonical_name, brand_id, manufacturer_id, category_id, subcategory_id,
         product_family_id, gtin, package_quantity, package_unit, packaging_type,
         created_at, updated_at
  FROM product
  WHERE lower(canonical_name) LIKE lower(:pattern)
  ORDER BY canonical_name ASC
  LIMIT :limit

@NAME(findRecent)
  SELECT id, canonical_name, brand_id, manufacturer_id, category_id, subcategory_id,
         product_family_id, gtin, package_quantity, package_unit, packaging_type,
         created_at, updated_at
  FROM product
  ORDER BY created_at DESC
  LIMIT :limit
