-- SQL for FiscalDocumentRepository, loaded through ELSQL (see config/ElSqlConfiguration).
--
-- Every owner-scoped query filters on owner_user_id — a fiscal document is never
-- returned by id alone. The access key is the natural fiscal identity used for
-- idempotent import (docs/fiscal-core.md).

-- ON CONFLICT DO NOTHING makes a repeat import a no-op instead of a constraint
-- error (which would abort the transaction). 0 rows affected => this owner
-- already has the access key; the service then returns the stored document.
@NAME(insert)
  INSERT INTO fiscal_document (
      id, owner_user_id, access_key, document_number, series, issued_at,
      issuer_cnpj, issuer_legal_name, issuer_trade_name, issuer_address,
      total_amount, total_items_amount, total_discount)
  VALUES (
      :id, :ownerUserId, :accessKey, :documentNumber, :series, :issuedAt,
      :issuerCnpj, :issuerLegalName, :issuerTradeName, :issuerAddress,
      :totalAmount, :totalItemsAmount, :totalDiscount)
  ON CONFLICT (owner_user_id, access_key) DO NOTHING

@NAME(findByIdAndOwner)
  SELECT
      id, owner_user_id, access_key, document_number, series, issued_at,
      issuer_cnpj, issuer_legal_name, issuer_trade_name, issuer_address,
      total_amount, total_items_amount, total_discount, created_at
  FROM fiscal_document
  WHERE id = :id
    AND owner_user_id = :ownerUserId

@NAME(findByAccessKeyAndOwner)
  SELECT
      id, owner_user_id, access_key, document_number, series, issued_at,
      issuer_cnpj, issuer_legal_name, issuer_trade_name, issuer_address,
      total_amount, total_items_amount, total_discount, created_at
  FROM fiscal_document
  WHERE access_key = :accessKey
    AND owner_user_id = :ownerUserId

-- Administrative lookup (M006): any document, regardless of owner. Only reached
-- through /api/admin/** which is gated to ROLE_ADMIN.
@NAME(findByIdForAdmin)
  SELECT
      id, owner_user_id, access_key, document_number, series, issued_at,
      issuer_cnpj, issuer_legal_name, issuer_trade_name, issuer_address,
      total_amount, total_items_amount, total_discount, created_at
  FROM fiscal_document
  WHERE id = :id

@NAME(findAllByOwner)
  SELECT
      d.id, d.owner_user_id, d.access_key, d.document_number, d.series, d.issued_at,
      d.issuer_cnpj, d.issuer_legal_name, d.issuer_trade_name, d.issuer_address,
      d.total_amount, d.total_items_amount, d.total_discount, d.created_at,
      (SELECT count(*) FROM fiscal_item i WHERE i.fiscal_document_id = d.id) AS item_count
  FROM fiscal_document d
  WHERE d.owner_user_id = :ownerUserId
  ORDER BY d.issued_at DESC, d.created_at DESC
