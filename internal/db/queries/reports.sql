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
  FROM sales GROUP BY shop_id
) sales_agg ON sales_agg.shop_id = s.id
LEFT JOIN (
  SELECT party_id, SUM(amount) AS paid
  FROM payments WHERE party_type = 'shop' GROUP BY party_id
) pay_agg ON pay_agg.party_id = s.id
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
  FROM purchases GROUP BY factory_id
) pu_agg ON pu_agg.factory_id = f.id
LEFT JOIN (
  SELECT party_id, SUM(amount) AS paid
  FROM payments WHERE party_type = 'factory' GROUP BY party_id
) pay_agg ON pay_agg.party_id = f.id
ORDER BY balance DESC;

-- name: TodaySalesSummary :one
SELECT
  COUNT(*)::int AS count,
  COALESCE(SUM(total_amount), 0)::numeric(12,2) AS total
FROM sales
WHERE sale_date = CURRENT_DATE;

-- name: TodayPurchasesSummary :one
SELECT
  COUNT(*)::int AS count,
  COALESCE(SUM(total_amount), 0)::numeric(12,2) AS total
FROM purchases
WHERE purchase_date = CURRENT_DATE;

-- name: ProfitSummary :one
SELECT
  COALESCE(SUM((si.unit_price - p.purchase_price) * si.quantity), 0)::numeric(12,2) AS profit
FROM sale_items si
JOIN products p ON p.id = si.product_id;

-- name: LowStockProducts :many
SELECT * FROM products WHERE current_stock < 10 ORDER BY current_stock ASC;
