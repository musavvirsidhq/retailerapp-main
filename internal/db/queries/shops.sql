-- name: CreateShop :one
INSERT INTO shops (company_id, name, owner_name, primary_phone, secondary_phone, area, opening_balance)
VALUES ($1, $2, $3, $4, $5, $6, $7)
RETURNING *;

-- name: GetShop :one
SELECT * FROM shops WHERE id = $1 AND company_id = $2;

-- name: ListShops :many
SELECT * FROM shops WHERE company_id = $1 ORDER BY name;

-- name: UpdateShop :one
UPDATE shops
SET name = $3, owner_name = $4, primary_phone = $5, secondary_phone = $6, area = $7
WHERE id = $1 AND company_id = $2
RETURNING *;

-- name: DeleteShop :exec
DELETE FROM shops WHERE id = $1 AND company_id = $2;
