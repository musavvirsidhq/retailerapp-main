-- name: CreateAuditLogEntry :one
INSERT INTO audit_log (company_id, actor_user_id, action, entity_type, entity_id, reason, reversal_txn_id, inventory_impact, payment_impact)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
RETURNING *;

-- name: ListAuditLogByEntity :many
SELECT * FROM audit_log WHERE entity_type = $1 AND entity_id = $2 ORDER BY created_at DESC;
