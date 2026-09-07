-- SQL for CategoryRepository (M004), loaded through ELSQL.

@NAME(insert)
  INSERT INTO category (id, name)
  VALUES (:id, :name)

@NAME(findById)
  SELECT id, name, created_at, updated_at
  FROM category
  WHERE id = :id

@NAME(existsById)
  SELECT EXISTS (SELECT 1 FROM category WHERE id = :id)
