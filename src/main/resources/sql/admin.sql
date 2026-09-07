-- SQL for AdminRepository (M006), loaded through ELSQL.
--
-- These are platform-wide inspection reads, NOT owner-scoped. Every path that
-- runs them is gated to ROLE_ADMIN in SecurityConfig. Rows are capped with
-- :limit — no cursor pagination in M006. Consumer CPF/name is never stored or
-- selected (it is never persisted).

@NAME(counts)
  SELECT
      (SELECT count(*) FROM fiscal_document)                                     AS fiscal_documents,
      (SELECT count(*) FROM fiscal_item)                                         AS fiscal_items,
      (SELECT count(*) FROM product_resolution WHERE status = 'RESOLVED')        AS resolved_items,
      (SELECT count(*) FROM fiscal_item)
          - (SELECT count(*) FROM product_resolution WHERE status = 'RESOLVED')  AS unresolved_items,
      (SELECT count(*) FROM product)                                             AS catalog_products,
      (SELECT count(*) FROM app_user)                                            AS users

@NAME(fiscalDocuments)
  SELECT
      d.id, d.owner_user_id, d.access_key, d.issued_at, d.issuer_cnpj,
      COALESCE(d.issuer_trade_name, d.issuer_legal_name, d.issuer_cnpj) AS issuer_name,
      d.total_amount, d.created_at,
      (SELECT count(*) FROM fiscal_item i WHERE i.fiscal_document_id = d.id) AS item_count
  FROM fiscal_document d
  ORDER BY d.created_at DESC
  LIMIT :limit

@NAME(fiscalItems)
  SELECT
      i.id, i.fiscal_document_id, d.access_key, i.raw_description, i.supplier_item_code,
      i.gtin, i.gtin_taxable, i.quantity, i.unit, i.unit_price, i.total_price,
      r.status AS resolution_status, r.canonical_product_id
  FROM fiscal_item i
  JOIN fiscal_document d ON d.id = i.fiscal_document_id
  LEFT JOIN product_resolution r ON r.fiscal_item_id = i.id
  ORDER BY d.created_at DESC, i.line_number ASC
  LIMIT :limit

@NAME(resolutions)
  SELECT
      r.id, r.fiscal_item_id, i.raw_description AS fiscal_raw_description,
      r.status, r.method, r.canonical_product_id, p.canonical_name AS canonical_product_name,
      r.created_at, r.updated_at,
      (SELECT count(*) FROM resolution_evidence e WHERE e.resolution_id = r.id) AS evidence_count
  FROM product_resolution r
  JOIN fiscal_item i ON i.id = r.fiscal_item_id
  LEFT JOIN product p ON p.id = r.canonical_product_id
  ORDER BY r.updated_at DESC
  LIMIT :limit
