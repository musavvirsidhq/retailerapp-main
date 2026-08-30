-- name: CreateShop :one
INSERT INTO shops (name, owner_name, phone, area, opening_balance)
VALUES ($1, $2, $3, $4, $5)
RETURNING *;

-- name: GetShop :one
SELECT * FROM shops WHERE id = $1;

-- name: ListShops :many
SELECT * FROM shops ORDER BY name;

-- name: UpdateShop :one
UPDATE shops
SET name = $2, owner_name = $3, phone = $4, area = $5
WHERE id = $1
RETURNING *;

-- name: DeleteShop :exec
DELETE FROM shops WHERE id = $1;
