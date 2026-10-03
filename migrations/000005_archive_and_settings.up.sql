-- Cycle 5: archive instead of delete (customers, suppliers, products), the company's
-- "require photo for payments" setting, and indexes for the filtered/paged bill lists.

ALTER TABLE shops     ADD COLUMN archived_at TIMESTAMPTZ, ADD COLUMN archived_by INT REFERENCES users(id);
ALTER TABLE factories ADD COLUMN archived_at TIMESTAMPTZ, ADD COLUMN archived_by INT REFERENCES users(id);
ALTER TABLE products  ADD COLUMN archived_at TIMESTAMPTZ, ADD COLUMN archived_by INT REFERENCES users(id);

ALTER TABLE companies ADD COLUMN require_payment_photo BOOLEAN NOT NULL DEFAULT false;

-- List filters and paging (section 6)
CREATE INDEX IF NOT EXISTS idx_sales_company_date     ON sales(company_id, sale_date DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_purchases_company_date ON purchases(company_id, purchase_date DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_payments_company_date  ON payments(company_id, payment_date DESC, id DESC);
