-- name: CreateSubscription :one
INSERT INTO subscriptions (company_id, subscription_type, start_date, expiry_date, amount, purchase_date, status, granted_by)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
RETURNING *;

-- name: GetLatestSubscriptionByCompany :one
SELECT * FROM subscriptions WHERE company_id = $1 ORDER BY expiry_date DESC, id DESC LIMIT 1;

-- name: ListSubscriptionsByCompany :many
SELECT * FROM subscriptions WHERE company_id = $1 ORDER BY expiry_date DESC, id DESC;

-- name: UpdateSubscriptionExpiry :one
UPDATE subscriptions
SET expiry_date = $2, status = $3, modified_on = now()
WHERE id = $1
RETURNING *;
