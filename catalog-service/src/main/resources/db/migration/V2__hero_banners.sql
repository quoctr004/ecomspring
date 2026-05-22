-- Hero banner slideshow for the storefront homepage. Admin uploads image +
-- target URL via /admin/banners; storefront reads only the enabled ones,
-- sorted by display_order ASC. `image_url` is just a string — either an
-- external CDN URL or a path served by the asset host of choice.

CREATE TABLE IF NOT EXISTS hero_banners (
    id            BIGSERIAL    PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    subtitle      VARCHAR(500),
    image_url     VARCHAR(1024) NOT NULL,
    target_url    VARCHAR(1024),
    cta_label     VARCHAR(60),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL DEFAULT 100,
    starts_at     TIMESTAMP WITH TIME ZONE,
    ends_at       TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_hero_banners_enabled_order
    ON hero_banners (enabled, display_order);
