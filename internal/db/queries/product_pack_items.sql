-- name: DeleteProductPackItems :exec
DELETE FROM product_pack_items WHERE product_id = $1 AND company_id = $2;

-- name: CreateProductPackItem :one
INSERT INTO product_pack_items (company_id, product_id, size, quantity)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: ListProductPackItems :many
SELECT * FROM product_pack_items WHERE product_id = $1 AND company_id = $2
ORDER BY CASE size
    WHEN 'S' THEN 1 WHEN 'M' THEN 2 WHEN 'L' THEN 3 WHEN 'XL' THEN 4
    WHEN 'XXL' THEN 5 WHEN 'XXXL' THEN 6 ELSE 7
END;
