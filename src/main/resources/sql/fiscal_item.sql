-- SQL for FiscalItemRepository, loaded through ELSQL (see config/ElSqlConfiguration).
--
-- Always scoped through fiscal_document_id; ownership of the parent document is
-- enforced by FiscalDocumentService before any of these run. raw_description and
-- the fiscal identifiers are written and read verbatim — never canonicalized.

@NAME(insert)
  INSERT INTO fiscal_item (
      id, fiscal_document_id, line_number, raw_description, gtin, gtin_taxable,
      supplier_item_code, quantity, unit, unit_price, total_price, discount, ncm, cest)
  VALUES (
      :id, :fiscalDocumentId, :lineNumber, :rawDescription, :gtin, :gtinTaxable,
      :supplierItemCode, :quantity, :unit, :unitPrice, :totalPrice, :discount, :ncm, :cest)

@NAME(findAllByDocument)
  SELECT
      id, fiscal_document_id, line_number, raw_description, gtin, gtin_taxable,
      supplier_item_code, quantity, unit, unit_price, total_price, discount, ncm, cest
  FROM fiscal_item
  WHERE fiscal_document_id = :fiscalDocumentId
  ORDER BY line_number ASC
