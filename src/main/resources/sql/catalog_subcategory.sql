-- SQL for SubcategoryRepository (M004), loaded through ELSQL.

@NAME(insert)
  INSERT INTO subcategory (id, category_id, name)
  VALUES (:id, :categoryId, :name)

@NAME(findById)
  SELECT id, category_id, name, created_at, updated_at
  FROM subcategory
  WHERE id = :id

@NAME(existsById)
  SELECT EXISTS (SELECT 1 FROM subcategory WHERE id = :id)
