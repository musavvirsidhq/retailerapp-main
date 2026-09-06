-- Cycle 2: multi-tenant company/subscription/auth foundation + product/billing/cancellation schema.
-- Local dev data is disposable, so this rebuilds the schema rather than backfilling in place.

DROP TABLE IF EXISTS
    payments,
    sale_item_cost_consumptions,
    sale_items,
    sales,
    product_cost_layers,
    purchase_items,
    purchases,
    bill_sequences,
    products,
    subcategories,
    categories,
    units,
    shops,
    factories,
    users,
    subscriptions,
    companies,
    audit_log
CASCADE;

CREATE TABLE companies (
    id SERIAL PRIMARY KEY,
    company_name VARCHAR(150) NOT NULL,
    company_code VARCHAR(30) UNIQUE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    joining_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_on TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_on TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by INT,
    modified_by INT,
    version INT NOT NULL DEFAULT 1
);

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    company_id INT NULL, -- NULL = platform-level SUPER_ADMIN
    name VARCHAR(100) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    user_type VARCHAR(20) NOT NULL CHECK (user_type IN ('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')),
    purchase_access BOOLEAN NOT NULL DEFAULT false,
    sales_access BOOLEAN NOT NULL DEFAULT false,
    sales_below_cost_approve BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT chk_super_admin_no_company CHECK (
        (user_type = 'SUPER_ADMIN' AND company_id IS NULL) OR
        (user_type != 'SUPER_ADMIN' AND company_id IS NOT NULL)
    )
);
CREATE INDEX idx_users_company ON users(company_id);

ALTER TABLE companies ADD CONSTRAINT fk_companies_created_by FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE companies ADD CONSTRAINT fk_companies_modified_by FOREIGN KEY (modified_by) REFERENCES users(id);

CREATE TABLE subscriptions (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    subscription_type VARCHAR(20) NOT NULL CHECK (subscription_type IN ('TRIAL', 'ANNUAL', 'EXTENDED')),
    start_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    purchase_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'TRIAL', 'EXPIRED', 'SUSPENDED')),
    granted_by INT REFERENCES users(id),
    created_on TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_on TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INT NOT NULL DEFAULT 1
);
CREATE INDEX idx_subscriptions_company ON subscriptions(company_id, expiry_date DESC);

CREATE TABLE categories (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (company_id, name)
);

CREATE TABLE subcategories (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    category_id INT NOT NULL REFERENCES categories(id),
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (category_id, name)
);

CREATE TABLE units (
    code VARCHAR(20) PRIMARY KEY,
    label VARCHAR(30) NOT NULL
);
INSERT INTO units (code, label) VALUES
    ('PIECE', 'Piece'),
    ('BOX', 'Box'),
    ('PACK', 'Pack'),
    ('KG', 'Kilogram'),
    ('GRAM', 'Gram'),
    ('LITRE', 'Litre'),
    ('ML', 'Millilitre'),
    ('METER', 'Meter'),
    ('SET', 'Set'),
    ('DOZEN', 'Dozen');

CREATE TABLE factories (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    name VARCHAR(150) NOT NULL,
    contact_person VARCHAR(100),
    primary_phone VARCHAR(20) NOT NULL,
    secondary_phone VARCHAR(20),
    address TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT chk_factory_phone_distinct CHECK (secondary_phone IS NULL OR secondary_phone <> primary_phone)
);
CREATE INDEX idx_factories_company ON factories(company_id);

CREATE TABLE shops (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    name VARCHAR(150) NOT NULL,
    owner_name VARCHAR(100),
    primary_phone VARCHAR(20) NOT NULL,
    secondary_phone VARCHAR(20),
    area VARCHAR(100),
    opening_balance NUMERIC(12,2) DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT chk_shop_phone_distinct CHECK (secondary_phone IS NULL OR secondary_phone <> primary_phone)
);
CREATE INDEX idx_shops_company ON shops(company_id);

CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    name VARCHAR(150) NOT NULL,
    sku VARCHAR(60) NOT NULL,
    unit VARCHAR(20) NOT NULL REFERENCES units(code),
    category_id INT NOT NULL REFERENCES categories(id),
    subcategory_id INT REFERENCES subcategories(id),
    current_selling_price NUMERIC(10,2) NOT NULL DEFAULT 0,
    current_stock NUMERIC(12,3) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (company_id, sku)
);
CREATE INDEX idx_products_company ON products(company_id);

CREATE TABLE bill_sequences (
    company_id INT NOT NULL REFERENCES companies(id),
    bill_type VARCHAR(10) NOT NULL CHECK (bill_type IN ('SALE', 'PURCHASE')),
    next_number INT NOT NULL DEFAULT 1,
    PRIMARY KEY (company_id, bill_type)
);

CREATE TABLE purchases (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    bill_number VARCHAR(30) NOT NULL,
    factory_id INT NOT NULL REFERENCES factories(id),
    invoice_no VARCHAR(50),
    purchase_date DATE NOT NULL DEFAULT CURRENT_DATE,
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    amount_paid NUMERIC(12,2) NOT NULL DEFAULT 0,
    status VARCHAR(12) NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED', 'CANCELLED')),
    cancelled_reason TEXT,
    cancelled_by INT REFERENCES users(id),
    cancelled_at TIMESTAMPTZ,
    created_by INT REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (company_id, bill_number)
);
CREATE INDEX idx_purchases_company ON purchases(company_id);

CREATE TABLE purchase_items (
    id SERIAL PRIMARY KEY,
    purchase_id INT NOT NULL REFERENCES purchases(id) ON DELETE CASCADE,
    product_id INT NOT NULL REFERENCES products(id),
    unit VARCHAR(20) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL
);

CREATE TABLE product_cost_layers (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    product_id INT NOT NULL REFERENCES products(id),
    purchase_bill_item_id INT NOT NULL REFERENCES purchase_items(id),
    buying_price NUMERIC(10,2) NOT NULL,
    quantity_received NUMERIC(12,3) NOT NULL,
    quantity_remaining NUMERIC(12,3) NOT NULL,
    created_on TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by INT REFERENCES users(id),
    status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DEPLETED', 'CANCELLED'))
);
CREATE INDEX idx_cost_layers_fifo ON product_cost_layers(product_id, created_on) WHERE status = 'ACTIVE';

CREATE TABLE sales (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    bill_number VARCHAR(30) NOT NULL,
    shop_id INT NOT NULL REFERENCES shops(id),
    sale_date DATE NOT NULL DEFAULT CURRENT_DATE,
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    amount_paid NUMERIC(12,2) NOT NULL DEFAULT 0,
    payment_type VARCHAR(10) NOT NULL CHECK (payment_type IN ('cash', 'credit')),
    status VARCHAR(12) NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED', 'CANCELLED')),
    cancelled_reason TEXT,
    cancelled_by INT REFERENCES users(id),
    cancelled_at TIMESTAMPTZ,
    created_by INT REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (company_id, bill_number)
);
CREATE INDEX idx_sales_company ON sales(company_id);

CREATE TABLE sale_items (
    id SERIAL PRIMARY KEY,
    sale_id INT NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    product_id INT NOT NULL REFERENCES products(id),
    unit VARCHAR(20) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL,
    below_cost BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE sale_item_cost_consumptions (
    id SERIAL PRIMARY KEY,
    sale_item_id INT NOT NULL REFERENCES sale_items(id) ON DELETE CASCADE,
    product_cost_layer_id INT NOT NULL REFERENCES product_cost_layers(id),
    quantity NUMERIC(12,3) NOT NULL,
    unit_cost NUMERIC(10,2) NOT NULL
);

CREATE TABLE payments (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    party_type VARCHAR(10) NOT NULL CHECK (party_type IN ('shop', 'factory')),
    party_id INT NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    payment_mode VARCHAR(20) NOT NULL,
    payment_date DATE NOT NULL DEFAULT CURRENT_DATE,
    notes TEXT,
    created_by INT REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_payments_company ON payments(company_id);

CREATE TABLE audit_log (
    id SERIAL PRIMARY KEY,
    company_id INT REFERENCES companies(id),
    actor_user_id INT REFERENCES users(id),
    action VARCHAR(30) NOT NULL,
    entity_type VARCHAR(20) NOT NULL,
    entity_id INT NOT NULL,
    reason TEXT,
    reversal_txn_id INT,
    inventory_impact JSONB,
    payment_impact JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_company ON audit_log(company_id);
