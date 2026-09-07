-- SQL for ProductFamilyRepository (M004), loaded through ELSQL.

@NAME(insert)
  INSERT INTO product_family (id, brand_id, name)
  VALUES (:id, :brandId, :name)

@NAME(findById)
  SELECT id, brand_id, name, created_at, updated_at
  FROM product_family
  WHERE id = :id

@NAME(existsById)
  SELECT EXISTS (SELECT 1 FROM product_family WHERE id = :id)
