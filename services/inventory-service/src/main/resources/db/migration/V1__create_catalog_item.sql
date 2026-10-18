CREATE TABLE catalog_item (
    sku varchar(64) PRIMARY KEY,
    unit_price_minor bigint NOT NULL CHECK (unit_price_minor > 0),
    currency varchar(3) NOT NULL CHECK (currency = 'INR'),
    available_quantity bigint NOT NULL CHECK (available_quantity >= 0),
    CONSTRAINT valid_catalog_sku CHECK (sku ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$')
);
