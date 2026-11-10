-- Phase 2 / V007: Inventory, Units, Recipes, Stock Locations, Append-Only Stock Movements, Suppliers & Purchase Orders

CREATE TABLE units (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    code                VARCHAR(32)   NOT NULL,
    name                VARCHAR(80)   NOT NULL,
    unit_type           VARCHAR(32)   NOT NULL,
    base_unit_id        UUID          REFERENCES units(id),
    conversion_factor   NUMERIC(14,6) NOT NULL DEFAULT 1.000000,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_units_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_units_type CHECK (unit_type IN ('WEIGHT', 'VOLUME', 'COUNT')),
    CONSTRAINT chk_units_factor_pos CHECK (conversion_factor > 0.000000)
);

CREATE TABLE ingredients (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants(id),
    sku             VARCHAR(80)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    unit_id         UUID          NOT NULL REFERENCES units(id),
    reorder_level   NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    average_cost    NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_ingredients_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT chk_ingredients_non_neg CHECK (reorder_level >= 0.0000 AND average_cost >= 0.0000)
);

CREATE TABLE stock_locations (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    branch_id       UUID         NOT NULL REFERENCES branches(id),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    default_location BOOLEAN     NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_stock_locations_branch_code UNIQUE (tenant_id, branch_id, code)
);

CREATE TABLE recipes (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    product_id          UUID          NOT NULL REFERENCES products(id),
    product_variant_id  UUID          REFERENCES product_variants(id),
    yield_quantity      NUMERIC(14,4) NOT NULL DEFAULT 1.0000,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_recipes_product_variant UNIQUE NULLS NOT DISTINCT (tenant_id, product_id, product_variant_id),
    CONSTRAINT chk_recipes_yield_pos CHECK (yield_quantity > 0.0000)
);

CREATE TABLE recipe_items (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants(id),
    recipe_id       UUID          NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    ingredient_id   UUID          NOT NULL REFERENCES ingredients(id),
    unit_id         UUID          NOT NULL REFERENCES units(id),
    quantity        NUMERIC(14,4) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recipe_items_ingredient UNIQUE (recipe_id, ingredient_id),
    CONSTRAINT chk_recipe_items_qty_pos CHECK (quantity > 0.0000)
);

CREATE TABLE suppliers (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    contact_name    VARCHAR(150),
    phone           VARCHAR(50),
    email           VARCHAR(200),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_suppliers_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE purchase_orders (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    branch_id           UUID          NOT NULL REFERENCES branches(id),
    supplier_id         UUID          NOT NULL REFERENCES suppliers(id),
    stock_location_id   UUID          NOT NULL REFERENCES stock_locations(id),
    created_by_user_id  UUID          NOT NULL REFERENCES users(id),
    po_number           VARCHAR(64)   NOT NULL,
    status              VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    currency_code       CHAR(3)       NOT NULL,
    total_amount        NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    ordered_at          TIMESTAMPTZ,
    received_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_purchase_orders_branch_num UNIQUE (tenant_id, branch_id, po_number),
    CONSTRAINT chk_purchase_orders_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED')),
    CONSTRAINT chk_purchase_orders_total CHECK (total_amount >= 0.0000)
);

CREATE INDEX idx_purchase_orders_branch_status ON purchase_orders (tenant_id, branch_id, status);

CREATE TABLE purchase_order_items (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    branch_id           UUID          NOT NULL REFERENCES branches(id),
    purchase_order_id   UUID          NOT NULL REFERENCES purchase_orders(id) ON DELETE RESTRICT,
    ingredient_id       UUID          NOT NULL REFERENCES ingredients(id),
    unit_id             UUID          NOT NULL REFERENCES units(id),
    ordered_quantity    NUMERIC(14,4) NOT NULL,
    received_quantity   NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    unit_cost           NUMERIC(14,4) NOT NULL,
    line_total          NUMERIC(14,4) NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_po_items_quantities CHECK (ordered_quantity > 0.0000 AND received_quantity >= 0.0000),
    CONSTRAINT chk_po_items_costs CHECK (unit_cost >= 0.0000 AND line_total >= 0.0000)
);

CREATE INDEX idx_purchase_order_items_po ON purchase_order_items (purchase_order_id);

-- Append-only immutable stock movement ledger
CREATE TABLE stock_movements (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    branch_id           UUID          NOT NULL REFERENCES branches(id),
    stock_location_id   UUID          NOT NULL REFERENCES stock_locations(id),
    ingredient_id       UUID          NOT NULL REFERENCES ingredients(id),
    actor_user_id       UUID          REFERENCES users(id),
    movement_type       VARCHAR(32)   NOT NULL,
    quantity_delta      NUMERIC(14,4) NOT NULL,
    unit_cost           NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    reference_type      VARCHAR(64),
    reference_id        UUID,
    reason              VARCHAR(300),
    idempotency_key     VARCHAR(128)  NOT NULL,
    occurred_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stock_movements_idempotency UNIQUE (tenant_id, branch_id, idempotency_key),
    CONSTRAINT chk_stock_movements_type CHECK (movement_type IN ('PURCHASE_RECEIPT', 'SALE_DEDUCTION', 'WASTE', 'ADJUSTMENT', 'TRANSFER_IN', 'TRANSFER_OUT', 'OPENING_STOCK')),
    CONSTRAINT chk_stock_movements_nonzero CHECK (quantity_delta <> 0.0000)
);

CREATE INDEX idx_stock_movements_lookup ON stock_movements (tenant_id, branch_id, ingredient_id, occurred_at DESC);

CREATE TRIGGER trg_stock_movements_immutable
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_mutation();

-- Materialized projection of current stock balances with optimistic locking
CREATE TABLE stock_balances (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    branch_id           UUID          NOT NULL REFERENCES branches(id),
    stock_location_id   UUID          NOT NULL REFERENCES stock_locations(id),
    ingredient_id       UUID          NOT NULL REFERENCES ingredients(id),
    quantity_on_hand    NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    last_movement_at    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_stock_balances_location_ing UNIQUE (tenant_id, branch_id, stock_location_id, ingredient_id)
);

CREATE INDEX idx_stock_balances_branch ON stock_balances (tenant_id, branch_id, ingredient_id);
