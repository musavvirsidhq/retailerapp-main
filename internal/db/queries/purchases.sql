-- name: CreatePurchase :one
INSERT INTO purchases (factory_id, invoice_no, total_amount, amount_paid, created_by)
VALUES ($1, $2, $3, $4, $5)
RETURNING *;

-- name: CreatePurchaseItem :one
INSERT INTO purchase_items (purchase_id, product_id, quantity, unit_price, line_total)
VALUES ($1, $2, $3, $4, $5)
RETURNING *;

-- name: IncrementProductStock :exec
UPDATE products SET current_stock = current_stock + $2 WHERE id = $1;

-- name: ListPurchases :many
SELECT p.id, p.factory_id, f.name AS factory_name, p.invoice_no, p.purchase_date,
       p.total_amount, p.amount_paid, p.created_at
FROM purchases p
JOIN factories f ON f.id = p.factory_id
ORDER BY p.purchase_date DESC, p.id DESC;

-- name: ListPurchaseItems :many
SELECT pi.id, pi.purchase_id, pi.product_id, pr.name AS product_name,
       pi.quantity, pi.unit_price, pi.line_total
FROM purchase_items pi
JOIN products pr ON pr.id = pi.product_id
WHERE pi.purchase_id = $1;
