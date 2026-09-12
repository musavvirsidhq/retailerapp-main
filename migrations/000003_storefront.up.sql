-- Cycle 3: product photos, fixed-assortment size packs, and a public storefront
-- (browse + place a no-payment order via COD or "contact us") per company.

ALTER TABLE products ADD COLUMN description TEXT;
ALTER TABLE products ADD COLUMN is_bundle BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE products ADD COLUMN storefront_visible BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE product_images (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    product_id INT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_product_images_product ON product_images(product_id, sort_order);

-- Composition of one "pack" of a bundle product, e.g. 1 pack = 2 S + 3 M + 3 L + 2 XL.
-- The existing products.unit ('PACK'/'DOZEN'/etc.) and current_stock keep counting whole
-- packs, exactly as billing/purchases already do - this table is purely descriptive metadata
-- for the storefront, so no existing sale/purchase/stock logic needs to change.
CREATE TABLE product_pack_items (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    product_id INT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    size VARCHAR(10) NOT NULL CHECK (size IN ('S', 'M', 'L', 'XL', 'XXL', 'XXXL', 'FREE')),
    quantity INT NOT NULL CHECK (quantity > 0),
    UNIQUE (product_id, size)
);

-- One row per company. Lets a company turn its public storefront on/off, and independently
-- turn off cash-on-delivery or the "reveal our contact number" purchase option once other
-- checkout methods (e.g. real payments) exist later.
CREATE TABLE company_storefront_settings (
    company_id INT PRIMARY KEY REFERENCES companies(id),
    enabled BOOLEAN NOT NULL DEFAULT false,
    cod_enabled BOOLEAN NOT NULL DEFAULT true,
    contact_enabled BOOLEAN NOT NULL DEFAULT true,
    contact_phone VARCHAR(20),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Orders placed by anonymous storefront visitors. Kept separate from `sales` (which requires
-- a registered shop with a running balance) since public buyers aren't onboarded shops - staff
-- follow up by phone/COD and record the eventual sale normally once fulfilled.
CREATE TABLE public_orders (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    customer_name VARCHAR(150) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    customer_address TEXT,
    fulfillment_method VARCHAR(10) NOT NULL CHECK (fulfillment_method IN ('COD', 'CONTACT')),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'CONFIRMED', 'CANCELLED')),
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_public_orders_company ON public_orders(company_id, created_at DESC);

CREATE TABLE public_order_items (
    id SERIAL PRIMARY KEY,
    order_id INT NOT NULL REFERENCES public_orders(id) ON DELETE CASCADE,
    product_id INT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL
);
CREATE INDEX idx_public_order_items_order ON public_order_items(order_id);
