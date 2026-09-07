-- SQL for BrandRepository (M004), loaded through ELSQL.

@NAME(insert)
  INSERT INTO brand (id, name, manufacturer_id)
  VALUES (:id, :name, :manufacturerId)

@NAME(findById)
  SELECT id, name, manufacturer_id, created_at, updated_at
  FROM brand
  WHERE id = :id

@NAME(existsById)
  SELECT EXISTS (SELECT 1 FROM brand WHERE id = :id)
