-- name: CreateProduct :one
INSERT INTO products (company_id, name, sku, unit, category_id, subcategory_id, current_selling_price, current_stock)
VALUES ($1, $2, $3, $4, $5, $6, $7, 0)
RETURNING *;

-- name: GetProduct :one
SELECT * FROM products WHERE id = $1 AND company_id = $2;

-- name: ListProducts :many
-- Active (non-archived) only: what lists, pickers and new bills may use (Cycle 5).
SELECT * FROM products WHERE company_id = $1 AND archived_at IS NULL ORDER BY name;

-- name: ListProductsIncludingArchived :many
SELECT * FROM products WHERE company_id = $1 ORDER BY archived_at IS NOT NULL, name;

-- name: GetArchivedProductBySku :one
-- An archived product keeps its SKU (UNIQUE (company_id, sku)), so a clash on create/update
-- may be with one of these.
SELECT * FROM products WHERE company_id = $1 AND sku = $2 AND archived_at IS NOT NULL;

-- name: UpdateProduct :one
UPDATE products
SET name = $3, sku = $4, unit = $5, category_id = $6, subcategory_id = $7, current_selling_price = $8
WHERE id = $1 AND company_id = $2
RETURNING *;

-- name: UpdateProductSellingPrice :one
UPDATE products SET current_selling_price = $3 WHERE id = $1 AND company_id = $2
RETURNING *;

-- name: ArchiveProduct :one
-- Cycle 5: soft delete; stock on hand is allowed (the app warns about it).
UPDATE products SET archived_at = now(), archived_by = $3
WHERE id = $1 AND company_id = $2 AND archived_at IS NULL
RETURNING *;

-- name: RestoreProduct :one
UPDATE products SET archived_at = NULL, archived_by = NULL
WHERE id = $1 AND company_id = $2 AND archived_at IS NOT NULL
RETURNING *;

-- name: UpdateProductStorefront :one
UPDATE products
SET description = $3, is_bundle = $4, storefront_visible = $5
WHERE id = $1 AND company_id = $2
RETURNING *;

-- name: ListStorefrontProducts :many
SELECT * FROM products WHERE company_id = $1 AND storefront_visible = true AND archived_at IS NULL ORDER BY name;

-- name: GetStorefrontProduct :one
SELECT * FROM products WHERE id = $1 AND company_id = $2 AND storefront_visible = true AND archived_at IS NULL;
