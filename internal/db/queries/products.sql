-- name: CreateProduct :one
INSERT INTO products (name, unit, purchase_price, selling_price, current_stock)
VALUES ($1, $2, $3, $4, $5)
RETURNING *;

-- name: GetProduct :one
SELECT * FROM products WHERE id = $1;

-- name: ListProducts :many
SELECT * FROM products ORDER BY name;

-- name: UpdateProduct :one
UPDATE products
SET name = $2, unit = $3, purchase_price = $4, selling_price = $5
WHERE id = $1
RETURNING *;

-- name: DeleteProduct :exec
DELETE FROM products WHERE id = $1;
