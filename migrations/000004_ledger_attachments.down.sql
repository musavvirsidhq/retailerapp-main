DROP INDEX IF EXISTS idx_payments_company_party;
DROP INDEX IF EXISTS idx_purchases_company_factory;
DROP INDEX IF EXISTS idx_sales_company_shop;

ALTER TABLE products DROP COLUMN IF EXISTS pinned;

DROP TABLE IF EXISTS transaction_attachments;
