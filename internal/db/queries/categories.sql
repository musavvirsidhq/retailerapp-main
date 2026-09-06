-- name: CreateCategory :one
INSERT INTO categories (company_id, name)
VALUES ($1, $2)
RETURNING *;

-- name: ListCategories :many
SELECT * FROM categories WHERE company_id = $1 ORDER BY name;

-- name: GetCategory :one
SELECT * FROM categories WHERE id = $1 AND company_id = $2;

-- name: CreateSubcategory :one
INSERT INTO subcategories (company_id, category_id, name)
VALUES ($1, $2, $3)
RETURNING *;

-- name: ListSubcategories :many
SELECT * FROM subcategories WHERE company_id = $1 AND category_id = $2 ORDER BY name;

-- name: GetSubcategory :one
SELECT * FROM subcategories WHERE id = $1 AND company_id = $2;
