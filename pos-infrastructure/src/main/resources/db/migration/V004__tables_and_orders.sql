-- Phase 2 / V004: Dining Areas, Tables, Orders, Immutable Item Snapshots, Discounts, Taxes & Status History

CREATE TABLE dining_areas (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    branch_id       UUID         NOT NULL REFERENCES branches(id),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    display_order   INT          NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_dining_areas_branch_code UNIQUE (tenant_id, branch_id, code)
);

CREATE TABLE dining_tables (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    branch_id       UUID         NOT NULL REFERENCES branches(id),
    dining_area_id  UUID         NOT NULL REFERENCES dining_areas(id),
    table_code      VARCHAR(32)  NOT NULL,
    display_name    VARCHAR(80)  NOT NULL,
    capacity        INT          NOT NULL DEFAULT 4,
    status          VARCHAR(32)  NOT NULL DEFAULT 'AVAILABLE',
    active_order_id UUID,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_dining_tables_branch_code UNIQUE (tenant_id, branch_id, table_code),
    CONSTRAINT chk_dining_tables_capacity CHECK (capacity > 0),
    CONSTRAINT chk_dining_tables_status CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'RESERVED', 'DIRTY', 'DISABLED'))
);

CREATE INDEX idx_dining_tables_branch_area ON dining_tables (tenant_id, branch_id, dining_area_id, status);

CREATE TABLE orders (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    branch_id           UUID          NOT NULL REFERENCES branches(id),
    terminal_id         UUID          REFERENCES terminals(id),
    dining_table_id     UUID          REFERENCES dining_tables(id),
    created_by_user_id  UUID          NOT NULL REFERENCES users(id),
    order_number        VARCHAR(64)   NOT NULL,
    order_type          VARCHAR(32)   NOT NULL,
    status              VARCHAR(32)   NOT NULL,
    currency_code       CHAR(3)       NOT NULL,
    subtotal_amount     NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    discount_amount     NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    tax_amount          NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    total_amount        NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    paid_amount         NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    guest_count         INT           NOT NULL DEFAULT 1,
    notes               VARCHAR(1000),
    opened_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_orders_branch_number UNIQUE (tenant_id, branch_id, order_number),
    CONSTRAINT chk_orders_type CHECK (order_type IN ('DINE_IN', 'TAKEAWAY', 'DELIVERY', 'COUNTER')),
    CONSTRAINT chk_orders_status CHECK (status IN ('DRAFT', 'OPEN', 'IN_KITCHEN', 'READY', 'SERVED', 'PAID', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT chk_orders_amounts_non_neg CHECK (
        subtotal_amount >= 0.0000 AND
        discount_amount >= 0.0000 AND
        tax_amount >= 0.0000 AND
        total_amount >= 0.0000 AND
        paid_amount >= 0.0000
    ),
    CONSTRAINT chk_orders_guest_count CHECK (guest_count > 0)
);

CREATE INDEX idx_orders_branch_status_time ON orders (tenant_id, branch_id, status, opened_at DESC);
CREATE INDEX idx_orders_branch_table ON orders (tenant_id, branch_id, dining_table_id) WHERE dining_table_id IS NOT NULL;

CREATE TABLE order_items (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID          NOT NULL REFERENCES tenants(id),
    branch_id               UUID          NOT NULL REFERENCES branches(id),
    order_id                UUID          NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    product_id              UUID          REFERENCES products(id) ON DELETE RESTRICT,
    product_variant_id      UUID          REFERENCES product_variants(id) ON DELETE RESTRICT,
    sku_snapshot            VARCHAR(80)   NOT NULL,
    product_name_snapshot   VARCHAR(200)  NOT NULL,
    variant_name_snapshot   VARCHAR(150),
    quantity                NUMERIC(14,4) NOT NULL,
    unit_price_snapshot     NUMERIC(14,4) NOT NULL,
    tax_rate_snapshot       NUMERIC(7,4)  NOT NULL DEFAULT 0.0000,
    tax_amount              NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    discount_amount         NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    line_total              NUMERIC(14,4) NOT NULL,
    kitchen_notes           VARCHAR(500),
    voided                  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_order_items_qty_pos CHECK (quantity > 0.0000),
    CONSTRAINT chk_order_items_amounts CHECK (
        unit_price_snapshot >= 0.0000 AND
        tax_amount >= 0.0000 AND
        discount_amount >= 0.0000 AND
        line_total >= 0.0000
    )
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_order_items_tenant_branch ON order_items (tenant_id, branch_id);

CREATE TABLE order_item_modifiers (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID          NOT NULL REFERENCES tenants(id),
    branch_id               UUID          NOT NULL REFERENCES branches(id),
    order_item_id           UUID          NOT NULL REFERENCES order_items(id) ON DELETE RESTRICT,
    modifier_id             UUID          REFERENCES modifiers(id) ON DELETE RESTRICT,
    modifier_name_snapshot  VARCHAR(150)  NOT NULL,
    unit_price_snapshot     NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    quantity                INT           NOT NULL DEFAULT 1,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_order_item_mod_qty CHECK (quantity > 0)
);

CREATE INDEX idx_order_item_modifiers_item ON order_item_modifiers (order_item_id);

CREATE TABLE order_discounts (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    branch_id           UUID          NOT NULL REFERENCES branches(id),
    order_id            UUID          NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    order_item_id       UUID          REFERENCES order_items(id) ON DELETE RESTRICT,
    discount_code       VARCHAR(64),
    discount_name       VARCHAR(150)  NOT NULL,
    discount_type       VARCHAR(32)   NOT NULL,
    discount_value      NUMERIC(14,4) NOT NULL,
    applied_amount      NUMERIC(14,4) NOT NULL,
    applied_by_user_id  UUID          NOT NULL REFERENCES users(id),
    approved_by_user_id UUID          REFERENCES users(id),
    reason              VARCHAR(300),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_order_discounts_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT chk_order_discounts_non_neg CHECK (discount_value >= 0.0000 AND applied_amount >= 0.0000)
);

CREATE INDEX idx_order_discounts_order ON order_discounts (order_id);

CREATE TABLE order_taxes (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    branch_id           UUID          NOT NULL REFERENCES branches(id),
    order_id            UUID          NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    tax_id              UUID          REFERENCES taxes(id) ON DELETE RESTRICT,
    tax_name_snapshot   VARCHAR(120)  NOT NULL,
    rate_percent_snapshot NUMERIC(7,4) NOT NULL,
    inclusive           BOOLEAN       NOT NULL DEFAULT FALSE,
    taxable_amount      NUMERIC(14,4) NOT NULL,
    tax_amount          NUMERIC(14,4) NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_order_taxes_non_neg CHECK (taxable_amount >= 0.0000 AND tax_amount >= 0.0000)
);

CREATE INDEX idx_order_taxes_order ON order_taxes (order_id);

CREATE TABLE order_status_history (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL REFERENCES tenants(id),
    branch_id           UUID         NOT NULL REFERENCES branches(id),
    order_id            UUID         NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    from_status         VARCHAR(32),
    to_status           VARCHAR(32)  NOT NULL,
    changed_by_user_id  UUID         NOT NULL REFERENCES users(id),
    reason              VARCHAR(300),
    changed_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_status_history_order ON order_status_history (order_id, changed_at);

CREATE TRIGGER trg_order_status_history_immutable
    BEFORE UPDATE OR DELETE ON order_status_history
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_mutation();
