-- name: CreateShop :one
INSERT INTO shops (company_id, name, owner_name, primary_phone, secondary_phone, area, opening_balance)
VALUES ($1, $2, $3, $4, $5, $6, $7)
RETURNING *;

-- name: GetShop :one
SELECT * FROM shops WHERE id = $1 AND company_id = $2;

-- name: ListShops :many
-- Active (non-archived) only: what lists, pickers and new bills may use (Cycle 5).
SELECT * FROM shops WHERE company_id = $1 AND archived_at IS NULL ORDER BY name;

-- name: ListShopsIncludingArchived :many
SELECT * FROM shops WHERE company_id = $1 ORDER BY archived_at IS NOT NULL, name;

-- name: UpdateShop :one
UPDATE shops
SET name = $3, owner_name = $4, primary_phone = $5, secondary_phone = $6, area = $7
WHERE id = $1 AND company_id = $2
RETURNING *;

-- name: ArchiveShop :one
-- Cycle 5: soft delete. Old bills, payments and ledgers keep pointing at the row.
UPDATE shops SET archived_at = now(), archived_by = $3
WHERE id = $1 AND company_id = $2 AND archived_at IS NULL
RETURNING *;

-- name: RestoreShop :one
UPDATE shops SET archived_at = NULL, archived_by = NULL
WHERE id = $1 AND company_id = $2 AND archived_at IS NOT NULL
RETURNING *;
