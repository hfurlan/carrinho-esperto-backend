-- SQL for ProductResolutionRepository (M005), loaded through ELSQL.
--
-- One resolution row per fiscal item (unique index). The service upserts:
-- insert on first resolve, update in place on re-resolve — never a second row.

@NAME(insert)
  INSERT INTO product_resolution (id, fiscal_item_id, canonical_product_id, status, method)
  VALUES (:id, :fiscalItemId, :canonicalProductId, :status, :method)

@NAME(updateOutcome)
  UPDATE product_resolution
     SET canonical_product_id = :canonicalProductId,
         status = :status,
         method = :method,
         updated_at = now()
   WHERE id = :id

@NAME(findByFiscalItemId)
  SELECT id, fiscal_item_id, canonical_product_id, status, method, created_at, updated_at
  FROM product_resolution
  WHERE fiscal_item_id = :fiscalItemId

@NAME(findById)
  SELECT id, fiscal_item_id, canonical_product_id, status, method, created_at, updated_at
  FROM product_resolution
  WHERE id = :id
