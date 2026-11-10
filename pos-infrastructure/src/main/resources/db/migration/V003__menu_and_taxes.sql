-- Phase 2 / V003: Menu, Variants, Modifiers, Taxes, and Branch Price/Availability Overrides

CREATE TABLE taxes (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants(id),
    code            VARCHAR(64)   NOT NULL,
    name            VARCHAR(120)  NOT NULL,
    rate_percent    NUMERIC(7,4)  NOT NULL,
    inclusive       BOOLEAN       NOT NULL DEFAULT FALSE,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_taxes_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_taxes_rate_range CHECK (rate_percent >= 0.0000 AND rate_percent <= 100.0000)
);

CREATE INDEX idx_taxes_tenant_active ON taxes (tenant_id, active);

CREATE TABLE categories (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    parent_id       UUID         REFERENCES categories(id),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    display_order   INT          NOT NULL DEFAULT 0,
    color_hex       VARCHAR(16),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_categories_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_categories_no_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX idx_categories_tenant_order ON categories (tenant_id, active, display_order);

CREATE TABLE products (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants(id),
    category_id     UUID          NOT NULL REFERENCES categories(id),
    tax_id          UUID          REFERENCES taxes(id),
    sku             VARCHAR(80)   NOT NULL,
    barcode         VARCHAR(100),
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    base_price      NUMERIC(14,4) NOT NULL,
    cost_price      NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    pricing_mode    VARCHAR(32)   NOT NULL DEFAULT 'FIXED',
    image_ref       VARCHAR(500),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_products_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT chk_products_base_price CHECK (base_price >= 0.0000),
    CONSTRAINT chk_products_cost_price CHECK (cost_price >= 0.0000),
    CONSTRAINT chk_products_pricing_mode CHECK (pricing_mode IN ('FIXED', 'WEIGHTED', 'OPEN'))
);

CREATE INDEX idx_products_tenant_category ON products (tenant_id, category_id, active);
CREATE INDEX idx_products_tenant_barcode ON products (tenant_id, barcode) WHERE barcode IS NOT NULL;

CREATE TABLE product_variants (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants(id),
    product_id      UUID          NOT NULL REFERENCES products(id),
    sku             VARCHAR(80)   NOT NULL,
    barcode         VARCHAR(100),
    name            VARCHAR(150)  NOT NULL,
    price_override  NUMERIC(14,4) NOT NULL,
    display_order   INT           NOT NULL DEFAULT 0,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_product_variants_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT chk_product_variants_price CHECK (price_override >= 0.0000)
);

CREATE INDEX idx_product_variants_product ON product_variants (tenant_id, product_id, active);

CREATE TABLE modifier_groups (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(150) NOT NULL,
    min_selections  INT          NOT NULL DEFAULT 0,
    max_selections  INT          NOT NULL DEFAULT 1,
    required        BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_modifier_groups_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_modifier_groups_bounds CHECK (min_selections >= 0 AND max_selections >= min_selections)
);

CREATE TABLE modifiers (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    modifier_group_id   UUID          NOT NULL REFERENCES modifier_groups(id),
    code                VARCHAR(64)   NOT NULL,
    name                VARCHAR(150)  NOT NULL,
    price_delta         NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    display_order       INT           NOT NULL DEFAULT 0,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_modifiers_group_code UNIQUE (tenant_id, modifier_group_id, code)
);

CREATE INDEX idx_modifiers_group ON modifiers (tenant_id, modifier_group_id, active);

CREATE TABLE product_modifier_groups (
    product_id          UUID        NOT NULL REFERENCES products(id),
    modifier_group_id   UUID        NOT NULL REFERENCES modifier_groups(id),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    display_order       INT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (product_id, modifier_group_id)
);

CREATE TABLE branch_product_overrides (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants(id),
    branch_id       UUID          NOT NULL REFERENCES branches(id),
    product_id      UUID          NOT NULL REFERENCES products(id),
    price_override  NUMERIC(14,4),
    tax_override_id UUID          REFERENCES taxes(id),
    available       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_branch_product_override UNIQUE (tenant_id, branch_id, product_id),
    CONSTRAINT chk_branch_override_price CHECK (price_override IS NULL OR price_override >= 0.0000)
);

CREATE INDEX idx_branch_product_overrides_lookup ON branch_product_overrides (tenant_id, branch_id, available);
