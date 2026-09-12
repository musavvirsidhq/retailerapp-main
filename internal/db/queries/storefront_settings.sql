-- name: GetStorefrontSettings :one
SELECT * FROM company_storefront_settings WHERE company_id = $1;

-- name: UpsertStorefrontSettings :one
INSERT INTO company_storefront_settings (company_id, enabled, cod_enabled, contact_enabled, contact_phone, updated_at)
VALUES ($1, $2, $3, $4, $5, now())
ON CONFLICT (company_id) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    cod_enabled = EXCLUDED.cod_enabled,
    contact_enabled = EXCLUDED.contact_enabled,
    contact_phone = EXCLUDED.contact_phone,
    updated_at = now()
RETURNING *;
