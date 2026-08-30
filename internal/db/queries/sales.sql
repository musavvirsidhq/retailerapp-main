-- name: CreateSale :one
INSERT INTO sales (shop_id, total_amount, amount_paid, payment_type)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: CreateSaleItem :one
INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, line_total)
VALUES ($1, $2, $3, $4, $5)
RETURNING *;

-- name: DecrementProductStock :one
UPDATE products
SET current_stock = current_stock - $2
WHERE id = $1 AND current_stock >= $2
RETURNING *;

-- name: ListSales :many
SELECT s.id, s.shop_id, sh.name AS shop_name, s.sale_date, s.total_amount, s.amount_paid, s.payment_type, s.created_at
FROM sales s
JOIN shops sh ON sh.id = s.shop_id
ORDER BY s.sale_date DESC, s.id DESC;

-- name: ListSaleItems :many
SELECT si.id, si.sale_id, si.product_id, pr.name AS product_name,
       si.quantity, si.unit_price, si.line_total
FROM sale_items si
JOIN products pr ON pr.id = si.product_id
WHERE si.sale_id = $1;
