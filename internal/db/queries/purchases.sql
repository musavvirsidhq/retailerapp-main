-- name: CreatePurchase :one
INSERT INTO purchases (company_id, bill_number, factory_id, invoice_no, total_amount, amount_paid, created_by)
VALUES ($1, $2, $3, $4, $5, $6, $7)
RETURNING *;

-- name: CreatePurchaseItem :one
INSERT INTO purchase_items (purchase_id, product_id, unit, quantity, unit_price, line_total)
VALUES ($1, $2, $3, $4, $5, $6)
RETURNING *;

-- name: IncrementProductStock :exec
UPDATE products SET current_stock = current_stock + $2 WHERE id = $1;

-- name: DecrementProductStockUnchecked :exec
UPDATE products SET current_stock = current_stock - $2 WHERE id = $1;

-- name: ListPurchases :many
SELECT p.id, p.company_id, p.bill_number, p.factory_id, f.name AS factory_name, p.invoice_no, p.purchase_date,
       p.total_amount, p.amount_paid, p.status, p.created_at
FROM purchases p
JOIN factories f ON f.id = p.factory_id
WHERE p.company_id = $1
ORDER BY p.purchase_date DESC, p.id DESC;

-- name: GetPurchaseByID :one
SELECT p.id, p.company_id, p.bill_number, p.factory_id, f.name AS factory_name, f.primary_phone AS factory_phone,
       p.invoice_no, p.purchase_date, p.total_amount, p.amount_paid, p.status,
       p.cancelled_reason, p.cancelled_by, p.cancelled_at, p.created_by, p.created_at
FROM purchases p
JOIN factories f ON f.id = p.factory_id
WHERE p.id = $1 AND p.company_id = $2;

-- name: ListPurchaseItems :many
SELECT pi.id, pi.purchase_id, pi.product_id, pr.name AS product_name, pr.sku AS product_sku,
       pi.unit, pi.quantity, pi.unit_price, pi.line_total
FROM purchase_items pi
JOIN products pr ON pr.id = pi.product_id
WHERE pi.purchase_id = $1;

-- name: CancelPurchase :one
UPDATE purchases
SET status = 'CANCELLED', cancelled_reason = $3, cancelled_by = $4, cancelled_at = now()
WHERE id = $1 AND company_id = $2 AND status = 'COMPLETED'
RETURNING *;
