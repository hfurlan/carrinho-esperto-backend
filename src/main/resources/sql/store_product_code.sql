-- SQL for StoreProductCodeRepository (M007), loaded through ELSQL.
--
-- Platform-owned store vocabulary: (store_id, code) -> canonical_product_id.
-- store_id is the issuer CNPJ (digits only). Unique on (store_id, code); a
-- conflicting create is rejected by the service, never overwritten here.

@NAME(insert)
  INSERT INTO store_product_code (id, store_id, code, canonical_product_id)
  VALUES (:id, :storeId, :code, :canonicalProductId)

@NAME(updateProduct)
  UPDATE store_product_code
     SET canonical_product_id = :canonicalProductId,
         updated_at = now()
   WHERE id = :id

@NAME(delete)
  DELETE FROM store_product_code
  WHERE id = :id

@NAME(findById)
  SELECT id, store_id, code, canonical_product_id, created_at, updated_at
  FROM store_product_code
  WHERE id = :id

@NAME(findByStoreAndCode)
  SELECT id, store_id, code, canonical_product_id, created_at, updated_at
  FROM store_product_code
  WHERE store_id = :storeId
    AND code = :code

-- Admin listing with optional filters. ELSQL only emits a clause when its
-- parameter is present and non-null, so an absent filter matches everything
-- without any untyped-NULL comparison.
@NAME(findFiltered)
  SELECT id, store_id, code, canonical_product_id, created_at, updated_at
  FROM store_product_code
  @WHERE
    @AND(:storeId)
      store_id = :storeId
    @AND(:codeLike)
      code ILIKE :codeLike
  ORDER BY updated_at DESC, id ASC
  LIMIT :limit
