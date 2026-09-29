-- name: CreateAttachment :one
INSERT INTO transaction_attachments (company_id, entity_type, entity_id, file_path, content_type, size_bytes, uploaded_by)
VALUES ($1, $2, $3, $4, $5, $6, $7)
RETURNING *;

-- name: ListAttachments :many
SELECT a.id, a.entity_type, a.entity_id, a.content_type, a.size_bytes, a.created_at,
       COALESCE(u.name, '')::text AS uploaded_by_name
FROM transaction_attachments a
LEFT JOIN users u ON u.id = a.uploaded_by
WHERE a.company_id = $1 AND a.entity_type = $2 AND a.entity_id = $3 AND a.deleted_at IS NULL
ORDER BY a.created_at, a.id;

-- name: GetAttachment :one
SELECT * FROM transaction_attachments
WHERE id = $1 AND company_id = $2 AND deleted_at IS NULL;

-- name: CountAttachments :one
SELECT COUNT(*)::int FROM transaction_attachments
WHERE company_id = $1 AND entity_type = $2 AND entity_id = $3 AND deleted_at IS NULL;

-- name: SoftDeleteAttachment :execrows
UPDATE transaction_attachments
SET deleted_at = now(), deleted_by = $3, delete_reason = $4
WHERE id = $1 AND company_id = $2 AND deleted_at IS NULL;

-- name: AttachmentCountsByEntity :many
SELECT entity_id, COUNT(*)::int AS photo_count
FROM transaction_attachments
WHERE company_id = $1 AND entity_type = $2 AND entity_id = ANY(@entity_ids::int[]) AND deleted_at IS NULL
GROUP BY entity_id;
