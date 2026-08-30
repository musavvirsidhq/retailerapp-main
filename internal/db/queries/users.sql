-- name: CreateUser :one
INSERT INTO users (name, username, password_hash, role)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: GetUserByUsername :one
SELECT * FROM users WHERE username = $1;

-- name: GetUser :one
SELECT * FROM users WHERE id = $1;

-- name: ListUsers :many
SELECT id, name, username, role, created_at FROM users ORDER BY name;
