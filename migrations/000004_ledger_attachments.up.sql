-- Cycle 4: proof photos on payments and sale/purchase bills, pinned "quick access" products,
-- and indexes for the per-customer / per-supplier ledger and "most recent activity" sorting.

-- Photos are financial evidence, so they are soft-deleted (deleted_at) and never removed from
-- disk, matching the Cycle 2 "do not physically delete financial history" rule.
CREATE TABLE transaction_attachments (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    entity_type VARCHAR(10) NOT NULL CHECK (entity_type IN ('payment', 'sale', 'purchase')),
    entity_id INT NOT NULL,
    file_path TEXT NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes INT NOT NULL,
    uploaded_by INT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    deleted_by INT REFERENCES users(id),
    delete_reason TEXT
);
CREATE INDEX idx_attachments_entity
    ON transaction_attachments(company_id, entity_type, entity_id)
    WHERE deleted_at IS NULL;

ALTER TABLE products ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_sales_company_shop ON sales(company_id, shop_id, sale_date DESC);
CREATE INDEX IF NOT EXISTS idx_purchases_company_factory ON purchases(company_id, factory_id, purchase_date DESC);
CREATE INDEX IF NOT EXISTS idx_payments_company_party ON payments(company_id, party_type, party_id, payment_date DESC);
