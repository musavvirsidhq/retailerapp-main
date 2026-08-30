-- name: CreateFactory :one
INSERT INTO factories (name, contact_person, phone, address)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: GetFactory :one
SELECT * FROM factories WHERE id = $1;

-- name: ListFactories :many
SELECT * FROM factories ORDER BY name;

-- name: UpdateFactory :one
UPDATE factories
SET name = $2, contact_person = $3, phone = $4, address = $5
WHERE id = $1
RETURNING *;

-- name: DeleteFactory :exec
DELETE FROM factories WHERE id = $1;
