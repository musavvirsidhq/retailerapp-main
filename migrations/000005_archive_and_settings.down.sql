DROP INDEX IF EXISTS idx_payments_company_date;
DROP INDEX IF EXISTS idx_purchases_company_date;
DROP INDEX IF EXISTS idx_sales_company_date;

ALTER TABLE companies DROP COLUMN IF EXISTS require_payment_photo;

ALTER TABLE products  DROP COLUMN IF EXISTS archived_by, DROP COLUMN IF EXISTS archived_at;
ALTER TABLE factories DROP COLUMN IF EXISTS archived_by, DROP COLUMN IF EXISTS archived_at;
ALTER TABLE shops     DROP COLUMN IF EXISTS archived_by, DROP COLUMN IF EXISTS archived_at;
