DROP TABLE IF EXISTS public_order_items;
DROP TABLE IF EXISTS public_orders;
DROP TABLE IF EXISTS company_storefront_settings;
DROP TABLE IF EXISTS product_pack_items;
DROP TABLE IF EXISTS product_images;

ALTER TABLE products DROP COLUMN IF EXISTS storefront_visible;
ALTER TABLE products DROP COLUMN IF EXISTS is_bundle;
ALTER TABLE products DROP COLUMN IF EXISTS description;
