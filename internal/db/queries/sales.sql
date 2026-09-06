-- name: CreateSale :one
INSERT INTO sales (company_id, bill_number, shop_id, total_amount, amount_paid, payment_type, created_by)
VALUES ($1, $2, $3, $4, $5, $6, $7)
RETURNING *;

-- name: CreateSaleItem :one
INSERT INTO sale_items (sale_id, product_id, unit, quantity, unit_price, line_total, below_cost)
VALUES ($1, $2, $3, $4, $5, $6, $7)
RETURNING *;

-- name: DecrementProductStock :one
UPDATE products
SET current_stock = current_stock - $2
WHERE id = $1 AND current_stock >= $2
RETURNING *;

-- name: IncrementProductStockUnchecked :exec
UPDATE products SET current_stock = current_stock + $2 WHERE id = $1;

-- name: ListSales :many
SELECT s.id, s.company_id, s.bill_number, s.shop_id, sh.name AS shop_name, s.sale_date, s.total_amount,
       s.amount_paid, s.payment_type, s.status, s.created_at
FROM sales s
JOIN shops sh ON sh.id = s.shop_id
WHERE s.company_id = $1
ORDER BY s.sale_date DESC, s.id DESC;

-- name: GetSaleByID :one
SELECT s.id, s.company_id, s.bill_number, s.shop_id, sh.name AS shop_name, sh.primary_phone AS shop_phone,
       s.sale_date, s.total_amount, s.amount_paid, s.payment_type, s.status,
       s.cancelled_reason, s.cancelled_by, s.cancelled_at, s.created_by, s.created_at
FROM sales s
JOIN shops sh ON sh.id = s.shop_id
WHERE s.id = $1 AND s.company_id = $2;

-- name: ListSaleItems :many
SELECT si.id, si.sale_id, si.product_id, pr.name AS product_name, pr.sku AS product_sku,
       si.unit, si.quantity, si.unit_price, si.line_total, si.below_cost
FROM sale_items si
JOIN products pr ON pr.id = si.product_id
WHERE si.sale_id = $1;

-- name: CancelSale :one
UPDATE sales
SET status = 'CANCELLED', cancelled_reason = $3, cancelled_by = $4, cancelled_at = now()
WHERE id = $1 AND company_id = $2 AND status = 'COMPLETED'
RETURNING *;
