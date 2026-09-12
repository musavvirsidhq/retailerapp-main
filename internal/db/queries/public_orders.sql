-- name: CreatePublicOrder :one
INSERT INTO public_orders (company_id, customer_name, customer_phone, customer_address, fulfillment_method, total_amount)
VALUES ($1, $2, $3, $4, $5, $6)
RETURNING *;

-- name: CreatePublicOrderItem :one
INSERT INTO public_order_items (order_id, product_id, quantity, unit_price, line_total)
VALUES ($1, $2, $3, $4, $5)
RETURNING *;

-- name: ListPublicOrders :many
SELECT * FROM public_orders WHERE company_id = $1 ORDER BY created_at DESC;

-- name: GetPublicOrder :one
SELECT * FROM public_orders WHERE id = $1 AND company_id = $2;

-- name: ListPublicOrderItems :many
SELECT poi.id, poi.order_id, poi.product_id, p.name AS product_name, poi.quantity, poi.unit_price, poi.line_total
FROM public_order_items poi
JOIN products p ON p.id = poi.product_id
WHERE poi.order_id = $1;

-- name: UpdatePublicOrderStatus :one
UPDATE public_orders SET status = $3 WHERE id = $1 AND company_id = $2
RETURNING *;
