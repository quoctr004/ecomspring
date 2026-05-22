-- Admin-managed shipping carrier catalog. Replaces the previous hardcoded
-- list in ShippingFeeController. `code` is the GHTK/GHN/etc. identifier.

CREATE TABLE IF NOT EXISTS shipping_carrier_config (
    code           VARCHAR(40)  PRIMARY KEY,
    name           VARCHAR(120) NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    supports_cod   BOOLEAN      NOT NULL DEFAULT TRUE,
    eta_hours      INT          NOT NULL DEFAULT 48,
    display_order  INT          NOT NULL DEFAULT 100,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO shipping_carrier_config (code, name, enabled, supports_cod, eta_hours, display_order) VALUES
    ('GHTK',        'Giao Hàng Tiết Kiệm', TRUE,  TRUE, 24, 10),
    ('GHN',         'Giao Hàng Nhanh',     FALSE, TRUE, 36, 20),
    ('VIETTELPOST', 'Viettel Post',        FALSE, TRUE, 48, 30)
ON CONFLICT (code) DO NOTHING;
