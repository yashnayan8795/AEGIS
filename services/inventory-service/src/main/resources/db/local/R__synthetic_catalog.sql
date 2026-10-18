-- Opt-in local/test fixture only. Never overwrite existing catalog/stock.
INSERT INTO catalog_item (sku, unit_price_minor, currency, available_quantity)
VALUES ('AEGIS_ITEM', 100, 'INR', 10), ('AEGIS_EMPTY', 250, 'INR', 0)
ON CONFLICT (sku) DO NOTHING;
