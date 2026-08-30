-- name: CreatePayment :one
INSERT INTO payments (party_type, party_id, amount, payment_mode, notes)
VALUES ($1, $2, $3, $4, $5)
RETURNING *;

-- name: ListPayments :many
SELECT * FROM payments ORDER BY payment_date DESC, id DESC;

-- name: ListPaymentsByParty :many
SELECT * FROM payments WHERE party_type = $1 AND party_id = $2 ORDER BY payment_date DESC, id DESC;

-- name: ShopBalance :one
SELECT
  (
    COALESCE((SELECT s.opening_balance FROM shops s WHERE s.id = $1), 0)
    + COALESCE((SELECT SUM(sa.total_amount) FROM sales sa WHERE sa.shop_id = $1), 0)
    - COALESCE((SELECT SUM(sa.amount_paid) FROM sales sa WHERE sa.shop_id = $1), 0)
    - COALESCE((SELECT SUM(pay.amount) FROM payments pay WHERE pay.party_type = 'shop' AND pay.party_id = $1), 0)
  )::numeric(12,2) AS balance;

-- name: FactoryBalance :one
SELECT
  (
    COALESCE((SELECT SUM(pu.total_amount) FROM purchases pu WHERE pu.factory_id = $1), 0)
    - COALESCE((SELECT SUM(pu.amount_paid) FROM purchases pu WHERE pu.factory_id = $1), 0)
    - COALESCE((SELECT SUM(pay.amount) FROM payments pay WHERE pay.party_type = 'factory' AND pay.party_id = $1), 0)
  )::numeric(12,2) AS balance;
