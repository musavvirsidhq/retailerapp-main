CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('admin', 'godown', 'sales', 'accountant')),
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE factories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    contact_person VARCHAR(100),
    phone VARCHAR(20),
    address TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE shops (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    owner_name VARCHAR(100),
    phone VARCHAR(20),
    area VARCHAR(100),
    opening_balance NUMERIC(12,2) DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    purchase_price NUMERIC(10,2) NOT NULL DEFAULT 0,
    selling_price NUMERIC(10,2) NOT NULL DEFAULT 0,
    current_stock NUMERIC(12,3) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE purchases (
    id SERIAL PRIMARY KEY,
    factory_id INT NOT NULL REFERENCES factories(id),
    invoice_no VARCHAR(50),
    purchase_date DATE NOT NULL DEFAULT CURRENT_DATE,
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    amount_paid NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_by INT REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE purchase_items (
    id SERIAL PRIMARY KEY,
    purchase_id INT NOT NULL REFERENCES purchases(id) ON DELETE CASCADE,
    product_id INT NOT NULL REFERENCES products(id),
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL
);

CREATE TABLE sales (
    id SERIAL PRIMARY KEY,
    shop_id INT NOT NULL REFERENCES shops(id),
    sale_date DATE NOT NULL DEFAULT CURRENT_DATE,
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    amount_paid NUMERIC(12,2) NOT NULL DEFAULT 0,
    payment_type VARCHAR(10) NOT NULL CHECK (payment_type IN ('cash','credit')),
    created_by INT REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE sale_items (
    id SERIAL PRIMARY KEY,
    sale_id INT NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    product_id INT NOT NULL REFERENCES products(id),
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL
);

CREATE TABLE payments (
    id SERIAL PRIMARY KEY,
    party_type VARCHAR(10) NOT NULL CHECK (party_type IN ('shop','factory')),
    party_id INT NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    payment_mode VARCHAR(20) NOT NULL,
    payment_date DATE NOT NULL DEFAULT CURRENT_DATE,
    notes TEXT,
    created_by INT REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT now()
);
