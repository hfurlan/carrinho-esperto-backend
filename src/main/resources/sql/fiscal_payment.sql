-- SQL for FiscalPaymentRepository, loaded through ELSQL (see config/ElSqlConfiguration).
--
-- Always scoped through fiscal_document_id; ownership of the parent document is
-- enforced by FiscalDocumentService before any of these run. This is only the
-- payment information printed on the receipt, not a personal-finance domain.

@NAME(insert)
  INSERT INTO fiscal_payment (
      id, fiscal_document_id, line_number, method, method_description, amount)
  VALUES (
      :id, :fiscalDocumentId, :lineNumber, :method, :methodDescription, :amount)

@NAME(findAllByDocument)
  SELECT id, fiscal_document_id, line_number, method, method_description, amount
  FROM fiscal_payment
  WHERE fiscal_document_id = :fiscalDocumentId
  ORDER BY line_number ASC
