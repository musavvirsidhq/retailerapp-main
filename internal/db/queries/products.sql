-- name: CreateProduct :one
INSERT INTO products (company_id, name, sku, unit, category_id, subcategory_id, current_selling_price, current_stock)
VALUES ($1, $2, $3, $4, $5, $6, $7, 0)
RETURNING *;

-- name: GetProduct :one
SELECT * FROM products WHERE id = $1 AND company_id = $2;

-- name: ListProducts :many
SELECT * FROM products WHERE company_id = $1 ORDER BY name;

-- name: UpdateProduct :one
UPDATE products
SET name = $3, sku = $4, unit = $5, category_id = $6, subcategory_id = $7, current_selling_price = $8
WHERE id = $1 AND company_id = $2
RETURNING *;

-- name: UpdateProductSellingPrice :one
UPDATE products SET current_selling_price = $3 WHERE id = $1 AND company_id = $2
RETURNING *;

-- name: DeleteProduct :exec
DELETE FROM products WHERE id = $1 AND company_id = $2;

-- name: UpdateProductStorefront :one
UPDATE products
SET description = $3, is_bundle = $4, storefront_visible = $5
WHERE id = $1 AND company_id = $2
RETURNING *;

-- name: ListStorefrontProducts :many
SELECT * FROM products WHERE company_id = $1 AND storefront_visible = true ORDER BY name;

-- name: GetStorefrontProduct :one
SELECT * FROM products WHERE id = $1 AND company_id = $2 AND storefront_visible = true;
