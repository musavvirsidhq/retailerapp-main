-- name: ListUnits :many
SELECT * FROM units ORDER BY code;

-- name: GetUnit :one
SELECT * FROM units WHERE code = $1;
