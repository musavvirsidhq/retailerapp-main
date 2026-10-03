-- name: CreateFactory :one
INSERT INTO factories (company_id, name, contact_person, primary_phone, secondary_phone, address)
VALUES ($1, $2, $3, $4, $5, $6)
RETURNING *;

-- name: GetFactory :one
SELECT * FROM factories WHERE id = $1 AND company_id = $2;

-- name: ListFactories :many
-- Active (non-archived) only: what lists, pickers and new bills may use (Cycle 5).
SELECT * FROM factories WHERE company_id = $1 AND archived_at IS NULL ORDER BY name;

-- name: ListFactoriesIncludingArchived :many
SELECT * FROM factories WHERE company_id = $1 ORDER BY archived_at IS NOT NULL, name;

-- name: UpdateFactory :one
UPDATE factories
SET name = $3, contact_person = $4, primary_phone = $5, secondary_phone = $6, address = $7
WHERE id = $1 AND company_id = $2
RETURNING *;

-- name: ArchiveFactory :one
-- Cycle 5: soft delete. Old bills, payments and ledgers keep pointing at the row.
UPDATE factories SET archived_at = now(), archived_by = $3
WHERE id = $1 AND company_id = $2 AND archived_at IS NULL
RETURNING *;

-- name: RestoreFactory :one
UPDATE factories SET archived_at = NULL, archived_by = NULL
WHERE id = $1 AND company_id = $2 AND archived_at IS NOT NULL
RETURNING *;
