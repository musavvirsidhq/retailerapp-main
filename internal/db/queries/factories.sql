-- name: CreateFactory :one
INSERT INTO factories (company_id, name, contact_person, primary_phone, secondary_phone, address)
VALUES ($1, $2, $3, $4, $5, $6)
RETURNING *;

-- name: GetFactory :one
SELECT * FROM factories WHERE id = $1 AND company_id = $2;

-- name: ListFactories :many
SELECT * FROM factories WHERE company_id = $1 ORDER BY name;

-- name: UpdateFactory :one
UPDATE factories
SET name = $3, contact_person = $4, primary_phone = $5, secondary_phone = $6, address = $7
WHERE id = $1 AND company_id = $2
RETURNING *;

-- name: DeleteFactory :exec
DELETE FROM factories WHERE id = $1 AND company_id = $2;
