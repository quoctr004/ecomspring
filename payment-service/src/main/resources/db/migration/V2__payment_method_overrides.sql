-- Admin-toggleable overrides for the storefront payment-method catalog.
-- Yaml ships the seed list (COD/SEPAY/MOMO); rows here let admin enable/disable
-- a method at runtime without redeploying. `display_order` controls FE ordering.

CREATE TABLE IF NOT EXISTS payment_method_overrides (
    code           VARCHAR(40)  PRIMARY KEY,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order  INT          NOT NULL DEFAULT 100,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
