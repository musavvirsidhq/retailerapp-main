-- name: CreateUser :one
INSERT INTO users (company_id, name, username, password_hash, user_type, purchase_access, sales_access, sales_below_cost_approve)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
RETURNING *;

-- name: GetUserByUsername :one
SELECT * FROM users WHERE username = $1;

-- name: GetUser :one
SELECT * FROM users WHERE id = $1;

-- name: ListUsers :many
SELECT id, name, username, user_type, created_at FROM users ORDER BY name;

-- name: ListCompanyUsers :many
SELECT * FROM users WHERE company_id = $1 ORDER BY name;

-- name: GetCompanyUser :one
SELECT * FROM users WHERE id = $1 AND company_id = $2;

-- name: UpdateUserPermissions :one
UPDATE users
SET purchase_access = $3, sales_access = $4, sales_below_cost_approve = $5
WHERE id = $1 AND company_id = $2
RETURNING *;

-- name: UpdateUserStatus :one
UPDATE users SET status = $3 WHERE id = $1 AND company_id = $2
RETURNING *;
