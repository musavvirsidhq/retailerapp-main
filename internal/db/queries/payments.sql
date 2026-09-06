-- name: CreatePayment :one
INSERT INTO payments (company_id, party_type, party_id, amount, payment_mode, notes, created_by)
VALUES ($1, $2, $3, $4, $5, $6, $7)
RETURNING *;

-- name: ListPayments :many
SELECT * FROM payments WHERE company_id = $1 ORDER BY payment_date DESC, id DESC;

-- name: ListPaymentsByParty :many
SELECT * FROM payments WHERE company_id = $1 AND party_type = $2 AND party_id = $3 ORDER BY payment_date DESC, id DESC;

-- name: ShopBalance :one
SELECT
  (
    COALESCE((SELECT s.opening_balance FROM shops s WHERE s.id = $1 AND s.company_id = $2), 0)
    + COALESCE((SELECT SUM(sa.total_amount) FROM sales sa WHERE sa.shop_id = $1 AND sa.company_id = $2 AND sa.status = 'COMPLETED'), 0)
    - COALESCE((SELECT SUM(sa.amount_paid) FROM sales sa WHERE sa.shop_id = $1 AND sa.company_id = $2 AND sa.status = 'COMPLETED'), 0)
    - COALESCE((SELECT SUM(pay.amount) FROM payments pay WHERE pay.party_type = 'shop' AND pay.party_id = $1 AND pay.company_id = $2), 0)
  )::numeric(12,2) AS balance;

-- name: FactoryBalance :one
SELECT
  (
    COALESCE((SELECT SUM(pu.total_amount) FROM purchases pu WHERE pu.factory_id = $1 AND pu.company_id = $2 AND pu.status = 'COMPLETED'), 0)
    - COALESCE((SELECT SUM(pu.amount_paid) FROM purchases pu WHERE pu.factory_id = $1 AND pu.company_id = $2 AND pu.status = 'COMPLETED'), 0)
    - COALESCE((SELECT SUM(pay.amount) FROM payments pay WHERE pay.party_type = 'factory' AND pay.party_id = $1 AND pay.company_id = $2), 0)
  )::numeric(12,2) AS balance;
