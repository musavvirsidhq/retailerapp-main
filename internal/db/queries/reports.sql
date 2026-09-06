-- name: ShopDues :many
SELECT
  s.id,
  s.name,
  (
    COALESCE(s.opening_balance, 0)
    + COALESCE(sales_agg.total, 0)
    - COALESCE(sales_agg.paid, 0)
    - COALESCE(pay_agg.paid, 0)
  )::numeric(12,2) AS balance
FROM shops s
LEFT JOIN (
  SELECT shop_id, SUM(total_amount) AS total, SUM(amount_paid) AS paid
  FROM sales WHERE sales.company_id = $1 AND status = 'COMPLETED' GROUP BY shop_id
) sales_agg ON sales_agg.shop_id = s.id
LEFT JOIN (
  SELECT party_id, SUM(amount) AS paid
  FROM payments WHERE payments.company_id = $1 AND party_type = 'shop' GROUP BY party_id
) pay_agg ON pay_agg.party_id = s.id
WHERE s.company_id = $1
ORDER BY balance DESC;

-- name: FactoryPayables :many
SELECT
  f.id,
  f.name,
  (
    COALESCE(pu_agg.total, 0)
    - COALESCE(pu_agg.paid, 0)
    - COALESCE(pay_agg.paid, 0)
  )::numeric(12,2) AS balance
FROM factories f
LEFT JOIN (
  SELECT factory_id, SUM(total_amount) AS total, SUM(amount_paid) AS paid
  FROM purchases WHERE purchases.company_id = $1 AND status = 'COMPLETED' GROUP BY factory_id
) pu_agg ON pu_agg.factory_id = f.id
LEFT JOIN (
  SELECT party_id, SUM(amount) AS paid
  FROM payments WHERE payments.company_id = $1 AND party_type = 'factory' GROUP BY party_id
) pay_agg ON pay_agg.party_id = f.id
WHERE f.company_id = $1
ORDER BY balance DESC;

-- name: TodaySalesSummary :one
SELECT
  COUNT(*)::int AS count,
  COALESCE(SUM(total_amount), 0)::numeric(12,2) AS total
FROM sales
WHERE company_id = $1 AND sale_date = CURRENT_DATE AND status = 'COMPLETED';

-- name: TodayPurchasesSummary :one
SELECT
  COUNT(*)::int AS count,
  COALESCE(SUM(total_amount), 0)::numeric(12,2) AS total
FROM purchases
WHERE company_id = $1 AND purchase_date = CURRENT_DATE AND status = 'COMPLETED';

-- name: ProfitSummary :one
SELECT
  COALESCE(SUM(si.line_total), 0)::numeric(12,2) -
  COALESCE((
    SELECT SUM(sicc.quantity * sicc.unit_cost)
    FROM sale_item_cost_consumptions sicc
    JOIN sale_items sii ON sii.id = sicc.sale_item_id
    JOIN sales ss ON ss.id = sii.sale_id
    WHERE ss.company_id = $1 AND ss.status = 'COMPLETED'
  ), 0)::numeric(12,2) AS profit
FROM sale_items si
JOIN sales s ON s.id = si.sale_id
WHERE s.company_id = $1 AND s.status = 'COMPLETED';

-- name: LowStockProducts :many
SELECT * FROM products WHERE company_id = $1 AND current_stock < 10 ORDER BY current_stock ASC;
