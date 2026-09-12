-- name: CreateProductImage :one
INSERT INTO product_images (company_id, product_id, url, sort_order)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: ListProductImages :many
SELECT * FROM product_images WHERE product_id = $1 AND company_id = $2 ORDER BY sort_order, id;

-- name: GetProductImage :one
SELECT * FROM product_images WHERE id = $1 AND company_id = $2;

-- name: DeleteProductImage :exec
DELETE FROM product_images WHERE id = $1 AND company_id = $2;
