-- SQL for ManufacturerRepository (M004), loaded through ELSQL. Catalog data is
-- platform-owned — no owner/user filter anywhere in this file.

@NAME(insert)
  INSERT INTO manufacturer (id, name)
  VALUES (:id, :name)

@NAME(findById)
  SELECT id, name, created_at, updated_at
  FROM manufacturer
  WHERE id = :id

@NAME(existsById)
  SELECT EXISTS (SELECT 1 FROM manufacturer WHERE id = :id)
