-- SQL for DashboardRepository (M006), loaded through ELSQL.
--
-- Every query is scoped to the authenticated owner (:ownerUserId) — the
-- dashboard shows only the caller's own supermarket spending. This is a small
-- read/aggregation layer over Fiscal + Resolution, NOT the future Analytics
-- bounded context.

-- Headline counters for the dashboard summary cards. Each figure is its own
-- scalar subquery — joining documents to items would fan out the rows and
-- multiply SUM(total_amount) by the item count.
@NAME(summary)
  SELECT
      total_spent,
      receipt_count,
      item_count,
      resolved_item_count,
      item_count - resolved_item_count AS unresolved_item_count
  FROM (
      SELECT
          (SELECT COALESCE(SUM(total_amount), 0) FROM fiscal_document
            WHERE owner_user_id = :ownerUserId) AS total_spent,
          (SELECT count(*) FROM fiscal_document
            WHERE owner_user_id = :ownerUserId) AS receipt_count,
          (SELECT count(*) FROM fiscal_item i
             JOIN fiscal_document d ON d.id = i.fiscal_document_id
            WHERE d.owner_user_id = :ownerUserId) AS item_count,
          (SELECT count(*) FROM product_resolution r
             JOIN fiscal_item i ON i.id = r.fiscal_item_id
             JOIN fiscal_document d ON d.id = i.fiscal_document_id
            WHERE d.owner_user_id = :ownerUserId AND r.status = 'RESOLVED') AS resolved_item_count
  ) s

-- Spending grouped by calendar month (issue date, in the SP timezone).
@NAME(spendingByMonth)
  SELECT
      to_char(date_trunc('month', d.issued_at AT TIME ZONE 'America/Sao_Paulo'), 'YYYY-MM') AS month,
      SUM(d.total_amount) AS total,
      COUNT(*)            AS receipt_count
  FROM fiscal_document d
  WHERE d.owner_user_id = :ownerUserId
  GROUP BY 1
  ORDER BY 1 ASC

-- Spending grouped by issuing supermarket, biggest first.
@NAME(spendingByIssuer)
  SELECT
      d.issuer_cnpj,
      COALESCE(d.issuer_trade_name, d.issuer_legal_name, d.issuer_cnpj) AS issuer_name,
      SUM(d.total_amount) AS total,
      COUNT(*)            AS receipt_count
  FROM fiscal_document d
  WHERE d.owner_user_id = :ownerUserId
  GROUP BY d.issuer_cnpj, d.issuer_trade_name, d.issuer_legal_name
  ORDER BY total DESC
