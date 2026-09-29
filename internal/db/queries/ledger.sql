-- name: CustomerDues :many
-- Same balance formula as ShopBalance/ShopDues, plus the timestamps the Android dues list
-- sorts on (most recent activity first).
SELECT
  s.id,
  s.name,
  s.primary_phone,
  s.area,
  (
    COALESCE(s.opening_balance, 0)
    + COALESCE(sa.total, 0)
    - COALESCE(sa.paid, 0)
    - COALESCE(pa.paid, 0)
  )::numeric(12,2) AS balance,
  sa.last_at::timestamptz AS last_sale_at,
  pa.last_at::timestamptz AS last_payment_at
FROM shops s
LEFT JOIN (
  SELECT shop_id, SUM(total_amount) AS total, SUM(amount_paid) AS paid, MAX(created_at) AS last_at
  FROM sales WHERE sales.company_id = $1 AND status = 'COMPLETED' GROUP BY shop_id
) sa ON sa.shop_id = s.id
LEFT JOIN (
  SELECT party_id, SUM(amount) AS paid, MAX(created_at) AS last_at
  FROM payments WHERE payments.company_id = $1 AND party_type = 'shop' GROUP BY party_id
) pa ON pa.party_id = s.id
WHERE s.company_id = $1;

-- name: SupplierDues :many
SELECT
  f.id,
  f.name,
  f.primary_phone,
  f.address AS area,
  (
    COALESCE(pu.total, 0)
    - COALESCE(pu.paid, 0)
    - COALESCE(pa.paid, 0)
  )::numeric(12,2) AS balance,
  pu.last_at::timestamptz AS last_purchase_at,
  pa.last_at::timestamptz AS last_payment_at
FROM factories f
LEFT JOIN (
  SELECT factory_id, SUM(total_amount) AS total, SUM(amount_paid) AS paid, MAX(created_at) AS last_at
  FROM purchases WHERE purchases.company_id = $1 AND status = 'COMPLETED' GROUP BY factory_id
) pu ON pu.factory_id = f.id
LEFT JOIN (
  SELECT party_id, SUM(amount) AS paid, MAX(created_at) AS last_at
  FROM payments WHERE payments.company_id = $1 AND party_type = 'factory' GROUP BY party_id
) pa ON pa.party_id = f.id
WHERE f.company_id = $1;

-- name: ListSalesByShop :many
SELECT s.id, s.bill_number, s.sale_date, s.total_amount, s.amount_paid, s.status, s.created_at,
       (SELECT COUNT(*) FROM sale_items si WHERE si.sale_id = s.id)::int AS item_count
FROM sales s
WHERE s.company_id = $1 AND s.shop_id = $2
ORDER BY s.sale_date, s.created_at, s.id;

-- name: ListPurchasesByFactory :many
SELECT p.id, p.bill_number, p.invoice_no, p.purchase_date, p.total_amount, p.amount_paid, p.status, p.created_at,
       (SELECT COUNT(*) FROM purchase_items pi WHERE pi.purchase_id = p.id)::int AS item_count
FROM purchases p
WHERE p.company_id = $1 AND p.factory_id = $2
ORDER BY p.purchase_date, p.created_at, p.id;

-- name: GetPayment :one
SELECT * FROM payments WHERE id = $1 AND company_id = $2;

-- name: FrequentSaleProducts :many
-- Pinned products first, then the products on the most non-cancelled sale bill lines in the
-- last 90 days, most recently used breaking ties.
WITH usage AS (
  SELECT si.product_id, COUNT(*) AS uses, MAX(s.created_at) AS last_used
  FROM sale_items si
  JOIN sales s ON s.id = si.sale_id
  WHERE s.company_id = $1 AND s.status = 'COMPLETED' AND s.sale_date >= CURRENT_DATE - 90
  GROUP BY si.product_id
)
SELECT p.id, p.name, p.sku, p.unit, p.current_selling_price, p.current_stock, p.pinned,
       COALESCE(u.uses, 0)::int AS uses
FROM products p
LEFT JOIN usage u ON u.product_id = p.id
WHERE p.company_id = $1 AND (p.pinned OR u.uses IS NOT NULL)
ORDER BY p.pinned DESC, u.uses DESC NULLS LAST, u.last_used DESC NULLS LAST, p.name
LIMIT $2;

-- name: FrequentPurchaseProducts :many
WITH usage AS (
  SELECT pi.product_id, COUNT(*) AS uses, MAX(pu.created_at) AS last_used
  FROM purchase_items pi
  JOIN purchases pu ON pu.id = pi.purchase_id
  WHERE pu.company_id = $1 AND pu.status = 'COMPLETED' AND pu.purchase_date >= CURRENT_DATE - 90
  GROUP BY pi.product_id
)
SELECT p.id, p.name, p.sku, p.unit, p.current_selling_price, p.current_stock, p.pinned,
       COALESCE(u.uses, 0)::int AS uses
FROM products p
LEFT JOIN usage u ON u.product_id = p.id
WHERE p.company_id = $1 AND (p.pinned OR u.uses IS NOT NULL)
ORDER BY p.pinned DESC, u.uses DESC NULLS LAST, u.last_used DESC NULLS LAST, p.name
LIMIT $2;

-- name: CustomerUsualProducts :many
-- Usually buys: a customer's most frequent items across their last 10 completed sales.
WITH recent AS (
  SELECT sales.id FROM sales
  WHERE sales.company_id = $1 AND sales.shop_id = $2 AND sales.status = 'COMPLETED'
  ORDER BY sales.created_at DESC
  LIMIT 10
)
SELECT p.id, p.name, p.sku, p.unit, p.current_selling_price, p.current_stock, p.pinned,
       COUNT(si.id)::int AS uses
FROM sale_items si
JOIN recent r ON r.id = si.sale_id
JOIN products p ON p.id = si.product_id
GROUP BY p.id
ORDER BY uses DESC, p.name
LIMIT $3;

-- name: CountPinnedProducts :one
SELECT COUNT(*)::int FROM products WHERE company_id = $1 AND pinned;

-- name: SetProductPinned :execrows
UPDATE products SET pinned = $3 WHERE id = $1 AND company_id = $2;
