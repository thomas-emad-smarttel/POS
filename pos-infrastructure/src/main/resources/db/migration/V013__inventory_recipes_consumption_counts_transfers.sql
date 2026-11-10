-- Phase 7 / V013: Inventory Categories, Item Policies, Unit Conversions, Versioned Recipes & Modifiers,
-- Immutable Ledger Extensions, Reservations, Waste, Physical Stock Counts, Branch Transfers & Alerts

CREATE TABLE inventory_categories (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(400),
    display_order   INT          NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_categories_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE unit_conversions (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID           NOT NULL REFERENCES tenants(id),
    inventory_item_id   UUID           REFERENCES ingredients(id) ON DELETE RESTRICT,
    from_unit_id        UUID           NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    to_unit_id          UUID           NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    conversion_factor   NUMERIC(18,6)  NOT NULL,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_unit_conversions_factor_pos CHECK (conversion_factor > 0.000000),
    CONSTRAINT chk_unit_conversions_distinct CHECK (from_unit_id <> to_unit_id),
    CONSTRAINT uq_unit_conversions_scope UNIQUE NULLS NOT DISTINCT (tenant_id, inventory_item_id, from_unit_id, to_unit_id)
);

CREATE INDEX idx_unit_conversions_lookup ON unit_conversions (tenant_id, from_unit_id, to_unit_id, active);

ALTER TABLE stock_locations
    ADD COLUMN location_type VARCHAR(32) NOT NULL DEFAULT 'MAIN_STORE',
    ADD CONSTRAINT chk_stock_locations_type CHECK (location_type IN (
        'DRY_STORAGE', 'COLD_ROOM', 'FREEZER', 'BAR', 'PREP_LINE', 'MAIN_STORE', 'OTHER'
    ));

ALTER TABLE ingredients
    ADD COLUMN branch_id              UUID          REFERENCES branches(id),
    ADD COLUMN category_id            UUID          REFERENCES inventory_categories(id),
    ADD COLUMN default_location_id    UUID          REFERENCES stock_locations(id),
    ADD COLUMN description            VARCHAR(1000),
    ADD COLUMN item_type              VARCHAR(32)   NOT NULL DEFAULT 'RAW_INGREDIENT',
    ADD COLUMN barcode                VARCHAR(100),
    ADD COLUMN minimum_stock_level    NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    ADD COLUMN tracking_policy        VARCHAR(32)   NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN expiry_tracking        BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN batch_tracking         BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN negative_stock_policy  VARCHAR(32)   NOT NULL DEFAULT 'INHERIT',
    ADD COLUMN costing_method         VARCHAR(32)   NOT NULL DEFAULT 'WEIGHTED_AVERAGE',
    ADD CONSTRAINT chk_ingredients_item_type CHECK (item_type IN (
        'RAW_INGREDIENT', 'PREPARED_INGREDIENT', 'SEMI_FINISHED', 'FINISHED_GOOD', 'PACKAGING', 'CONSUMABLE', 'OTHER'
    )),
    ADD CONSTRAINT chk_ingredients_tracking CHECK (tracking_policy IN ('STANDARD', 'BATCH_EXPIRY', 'NONE')),
    ADD CONSTRAINT chk_ingredients_neg_policy CHECK (negative_stock_policy IN ('INHERIT', 'DENY', 'ALLOW', 'SUPERVISOR_OVERRIDE')),
    ADD CONSTRAINT chk_ingredients_min_non_neg CHECK (minimum_stock_level >= 0.0000);

CREATE INDEX idx_ingredients_tenant_branch ON ingredients (tenant_id, branch_id, active);
CREATE INDEX idx_ingredients_barcode ON ingredients (tenant_id, barcode) WHERE barcode IS NOT NULL;

CREATE TABLE inventory_policies (
    id                                UUID PRIMARY KEY,
    tenant_id                         UUID          NOT NULL REFERENCES tenants(id),
    branch_id                         UUID          NOT NULL REFERENCES branches(id),
    commitment_mode                   VARCHAR(48)   NOT NULL DEFAULT 'RESERVE_ON_SEND_CONSUME_ON_PREP',
    allow_negative_stock              BOOLEAN       NOT NULL DEFAULT FALSE,
    low_stock_alert_enabled           BOOLEAN       NOT NULL DEFAULT TRUE,
    expiry_warning_days               INT           NOT NULL DEFAULT 7,
    high_waste_threshold_qty          NUMERIC(14,4) NOT NULL DEFAULT 50.0000,
    waste_approval_threshold_amount   NUMERIC(14,4) NOT NULL DEFAULT 100.0000,
    count_variance_approval_pct       NUMERIC(7,4)  NOT NULL DEFAULT 5.0000,
    transfer_approval_required        BOOLEAN       NOT NULL DEFAULT TRUE,
    allow_manual_consumption_reverse  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_policies_branch UNIQUE (tenant_id, branch_id),
    CONSTRAINT chk_inv_policy_commitment CHECK (commitment_mode IN (
        'RESERVE_ON_SEND_CONSUME_ON_PREP', 'RESERVE_ON_CONFIRM_CONSUME_ON_START', 'DIRECT_CONSUME_ON_SEND', 'DISABLED'
    ))
);

ALTER TABLE recipes
    ALTER COLUMN product_id DROP NOT NULL,
    ADD COLUMN branch_id          UUID         REFERENCES branches(id),
    ADD COLUMN recipe_name        VARCHAR(160) NOT NULL DEFAULT 'Recipe',
    ADD COLUMN sub_recipe         BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN active_version_id  UUID,
    ADD COLUMN validated          BOOLEAN      NOT NULL DEFAULT FALSE,
    DROP CONSTRAINT uq_recipes_product_variant;

CREATE UNIQUE INDEX uq_recipes_product_variant_branch
    ON recipes (tenant_id, branch_id, product_id, product_variant_id) NULLS NOT DISTINCT
    WHERE product_id IS NOT NULL;

CREATE TABLE recipe_versions (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID          NOT NULL REFERENCES tenants(id),
    branch_id              UUID          REFERENCES branches(id),
    recipe_id              UUID          NOT NULL REFERENCES recipes(id) ON DELETE RESTRICT,
    version_number         INT           NOT NULL,
    status                 VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    yield_quantity         NUMERIC(14,4) NOT NULL DEFAULT 1.0000,
    yield_unit_id          UUID          REFERENCES units(id),
    prep_loss_pct          NUMERIC(7,4)  NOT NULL DEFAULT 0.0000,
    prep_instructions      VARCHAR(2000),
    expected_prep_minutes  INT           NOT NULL DEFAULT 0,
    theoretical_cost       NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    validated              BOOLEAN       NOT NULL DEFAULT FALSE,
    published_at           TIMESTAMPTZ,
    created_by_user_id     UUID          REFERENCES users(id),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_recipe_versions_num UNIQUE (recipe_id, version_number),
    CONSTRAINT chk_recipe_versions_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_recipe_versions_yield_pos CHECK (yield_quantity > 0.0000),
    CONSTRAINT chk_recipe_versions_loss CHECK (prep_loss_pct >= 0.0000 AND prep_loss_pct < 100.0000)
);

CREATE TABLE recipe_version_ingredients (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL REFERENCES tenants(id),
    recipe_version_id  UUID          NOT NULL REFERENCES recipe_versions(id) ON DELETE RESTRICT,
    ingredient_id      UUID          REFERENCES ingredients(id) ON DELETE RESTRICT,
    sub_recipe_id      UUID          REFERENCES recipes(id) ON DELETE RESTRICT,
    unit_id            UUID          NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    quantity           NUMERIC(14,6) NOT NULL,
    waste_loss_pct     NUMERIC(7,4)  NOT NULL DEFAULT 0.0000,
    optional_flag      BOOLEAN       NOT NULL DEFAULT FALSE,
    alternative_group  VARCHAR(64),
    notes              VARCHAR(300),
    display_order      INT           NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rvi_target CHECK (
        (ingredient_id IS NOT NULL AND sub_recipe_id IS NULL) OR
        (ingredient_id IS NULL AND sub_recipe_id IS NOT NULL)
    ),
    CONSTRAINT chk_rvi_qty_pos CHECK (quantity > 0.000000)
);

CREATE INDEX idx_rvi_version ON recipe_version_ingredients (recipe_version_id, display_order);

CREATE TABLE recipe_modifier_mappings (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL REFERENCES tenants(id),
    recipe_version_id  UUID          NOT NULL REFERENCES recipe_versions(id) ON DELETE RESTRICT,
    modifier_id        UUID          NOT NULL REFERENCES modifiers(id) ON DELETE RESTRICT,
    action_type        VARCHAR(16)   NOT NULL,
    ingredient_id      UUID          REFERENCES ingredients(id) ON DELETE RESTRICT,
    sub_recipe_id      UUID          REFERENCES recipes(id) ON DELETE RESTRICT,
    unit_id            UUID          REFERENCES units(id) ON DELETE RESTRICT,
    quantity_delta     NUMERIC(14,6) NOT NULL DEFAULT 0.000000,
    scale_factor       NUMERIC(10,4),
    notes              VARCHAR(300),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rmm_action CHECK (action_type IN ('ADD', 'REMOVE', 'MULTIPLY', 'REPLACE'))
);

CREATE INDEX idx_rmm_version_modifier ON recipe_modifier_mappings (recipe_version_id, modifier_id);

ALTER TABLE stock_balances
    ADD COLUMN quantity_reserved     NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    ADD COLUMN average_unit_cost     NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    ADD COLUMN last_cost_updated_at  TIMESTAMPTZ,
    ADD CONSTRAINT chk_stock_balances_reserved_non_neg CHECK (quantity_reserved >= 0.0000);

ALTER TABLE stock_movements
    ADD COLUMN direction          VARCHAR(16)   NOT NULL DEFAULT 'IN',
    ADD COLUMN unit_id            UUID          REFERENCES units(id),
    ADD COLUMN original_quantity  NUMERIC(14,6),
    ADD COLUMN balance_before     NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    ADD COLUMN balance_after      NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    ADD COLUMN reserved_before    NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    ADD COLUMN reserved_after     NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    ADD COLUMN total_cost         NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    ADD COLUMN batch_number       VARCHAR(80),
    ADD COLUMN expiry_date        VARCHAR(32),
    ADD COLUMN approval_id        UUID          REFERENCES supervisor_approvals(id),
    ADD COLUMN outbox_event_id    UUID,
    DROP CONSTRAINT chk_stock_movements_type,
    ADD CONSTRAINT chk_stock_movements_type CHECK (movement_type IN (
        'OPENING_BALANCE', 'OPENING_STOCK', 'PURCHASE_RECEIPT', 'SALE_CONSUMPTION', 'SALE_DEDUCTION',
        'RESERVATION', 'RESERVATION_RELEASE', 'WASTE', 'DAMAGE', 'STOCK_ADJUSTMENT_IN', 'STOCK_ADJUSTMENT_OUT',
        'ADJUSTMENT', 'TRANSFER_OUT', 'TRANSFER_IN', 'CUSTOMER_RETURN', 'SUPPLIER_RETURN',
        'PRODUCTION_CONSUMPTION', 'PRODUCTION_OUTPUT', 'REVERSAL'
    )),
    ADD CONSTRAINT chk_stock_movements_dir CHECK (direction IN ('IN', 'OUT', 'RESERVE', 'RELEASE'));

CREATE INDEX idx_stock_movements_ref ON stock_movements (tenant_id, branch_id, reference_type, reference_id);

CREATE TABLE inventory_processed_events (
    outbox_event_id  UUID PRIMARY KEY,
    tenant_id        UUID        NOT NULL REFERENCES tenants(id),
    branch_id        UUID        REFERENCES branches(id),
    event_type       VARCHAR(80) NOT NULL,
    processed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE stock_reservations (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID        NOT NULL REFERENCES tenants(id),
    branch_id             UUID        NOT NULL REFERENCES branches(id),
    order_id              UUID        NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    source_event_id       UUID,
    status                VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    recipe_snapshot_json  TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_stock_reservations_order UNIQUE (tenant_id, branch_id, order_id),
    CONSTRAINT chk_stock_reservations_status CHECK (status IN (
        'ACTIVE', 'PARTIALLY_CONSUMED', 'CONSUMED', 'RELEASED', 'CANCELLED'
    ))
);

CREATE TABLE stock_reservation_items (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID          NOT NULL REFERENCES tenants(id),
    branch_id               UUID          NOT NULL REFERENCES branches(id),
    reservation_id          UUID          NOT NULL REFERENCES stock_reservations(id) ON DELETE RESTRICT,
    order_item_id           UUID          NOT NULL,
    kitchen_ticket_item_id  UUID,
    ingredient_id           UUID          NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    stock_location_id       UUID          NOT NULL REFERENCES stock_locations(id) ON DELETE RESTRICT,
    recipe_version_id       UUID          REFERENCES recipe_versions(id) ON DELETE RESTRICT,
    reserved_quantity       NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    consumed_quantity       NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    released_quantity       NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    status                  VARCHAR(32)   NOT NULL DEFAULT 'RESERVED',
    idempotency_key         VARCHAR(128)  NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_stock_res_items_idemp UNIQUE (tenant_id, branch_id, idempotency_key),
    CONSTRAINT chk_stock_res_items_status CHECK (status IN ('RESERVED', 'CONSUMED', 'RELEASED', 'CANCELLED')),
    CONSTRAINT chk_stock_res_items_qty CHECK (
        reserved_quantity >= 0.0000 AND consumed_quantity >= 0.0000 AND released_quantity >= 0.0000
    )
);

CREATE INDEX idx_stock_res_items_order_item ON stock_reservation_items (tenant_id, branch_id, order_item_id);

CREATE TABLE stock_waste_records (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID          NOT NULL REFERENCES tenants(id),
    branch_id               UUID          NOT NULL REFERENCES branches(id),
    stock_location_id       UUID          NOT NULL REFERENCES stock_locations(id),
    ingredient_id           UUID          NOT NULL REFERENCES ingredients(id),
    unit_id                 UUID          NOT NULL REFERENCES units(id),
    original_quantity       NUMERIC(14,6) NOT NULL,
    base_quantity           NUMERIC(14,4) NOT NULL,
    unit_cost               NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    total_cost              NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    reason_code             VARCHAR(48)   NOT NULL,
    notes                   VARCHAR(500),
    attachment_ref          VARCHAR(300),
    actor_user_id           UUID          NOT NULL REFERENCES users(id),
    supervisor_approval_id  UUID          REFERENCES supervisor_approvals(id),
    idempotency_key         VARCHAR(128)  NOT NULL,
    movement_id             UUID          REFERENCES stock_movements(id),
    occurred_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stock_waste_idempotency UNIQUE (tenant_id, branch_id, idempotency_key),
    CONSTRAINT chk_stock_waste_reason CHECK (reason_code IN (
        'PREPARATION_WASTE', 'SPOILAGE', 'EXPIRED', 'DAMAGED', 'INCORRECT_PREPARATION', 'CUSTOMER_RETURN_DISPOSAL', 'OTHER'
    ))
);

CREATE TABLE stock_counts (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID          NOT NULL REFERENCES tenants(id),
    branch_id            UUID          NOT NULL REFERENCES branches(id),
    count_number         VARCHAR(64)   NOT NULL,
    stock_location_id    UUID          NOT NULL REFERENCES stock_locations(id),
    category_id          UUID          REFERENCES inventory_categories(id),
    status               VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    created_by_user_id   UUID          NOT NULL REFERENCES users(id),
    submitted_by_user_id UUID          REFERENCES users(id),
    approved_by_user_id  UUID          REFERENCES users(id),
    posted_by_user_id    UUID          REFERENCES users(id),
    approval_id          UUID          REFERENCES supervisor_approvals(id),
    total_variance_cost  NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    notes                VARCHAR(500),
    submitted_at         TIMESTAMPTZ,
    approved_at          TIMESTAMPTZ,
    posted_at            TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_stock_counts_number UNIQUE (tenant_id, branch_id, count_number),
    CONSTRAINT chk_stock_counts_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'APPROVED', 'POSTED', 'RECOUNT_REQUESTED', 'CANCELLED'
    ))
);

CREATE TABLE stock_count_items (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID          NOT NULL REFERENCES tenants(id),
    stock_count_id             UUID          NOT NULL REFERENCES stock_counts(id) ON DELETE RESTRICT,
    ingredient_id              UUID          NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    system_quantity            NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    counted_quantity           NUMERIC(14,4),
    variance_quantity          NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    unit_cost                  NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    variance_cost              NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    recount_required           BOOLEAN       NOT NULL DEFAULT FALSE,
    original_counted_quantity  NUMERIC(14,4),
    notes                      VARCHAR(300),
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_stock_count_items_ing UNIQUE (stock_count_id, ingredient_id)
);

CREATE TABLE stock_transfers (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID         NOT NULL REFERENCES tenants(id),
    transfer_number         VARCHAR(64)  NOT NULL,
    source_branch_id        UUID         NOT NULL REFERENCES branches(id),
    source_location_id      UUID         NOT NULL REFERENCES stock_locations(id),
    destination_branch_id   UUID         NOT NULL REFERENCES branches(id),
    destination_location_id UUID         NOT NULL REFERENCES stock_locations(id),
    status                  VARCHAR(32)  NOT NULL DEFAULT 'REQUESTED',
    requested_by_user_id    UUID         NOT NULL REFERENCES users(id),
    approved_by_user_id     UUID         REFERENCES users(id),
    dispatched_by_user_id   UUID         REFERENCES users(id),
    received_by_user_id     UUID         REFERENCES users(id),
    notes                   VARCHAR(500),
    requested_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    approved_at             TIMESTAMPTZ,
    dispatched_at           TIMESTAMPTZ,
    received_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_stock_transfers_num UNIQUE (tenant_id, transfer_number),
    CONSTRAINT chk_stock_transfers_status CHECK (status IN (
        'REQUESTED', 'APPROVED', 'DISPATCHED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'
    )),
    CONSTRAINT chk_stock_transfers_diff_loc CHECK (source_location_id <> destination_location_id)
);

CREATE TABLE stock_transfer_items (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    stock_transfer_id   UUID          NOT NULL REFERENCES stock_transfers(id) ON DELETE RESTRICT,
    ingredient_id       UUID          NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    unit_id             UUID          NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    requested_quantity  NUMERIC(14,4) NOT NULL,
    dispatched_quantity NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    received_quantity   NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    damaged_quantity    NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    missing_quantity    NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    unit_cost           NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    notes               VARCHAR(300),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_stock_transfer_items_ing UNIQUE (stock_transfer_id, ingredient_id),
    CONSTRAINT chk_stock_transfer_items_qty CHECK (
        requested_quantity > 0.0000 AND dispatched_quantity >= 0.0000
        AND received_quantity >= 0.0000 AND damaged_quantity >= 0.0000 AND missing_quantity >= 0.0000
    )
);

CREATE TABLE inventory_alerts (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID          NOT NULL REFERENCES tenants(id),
    branch_id            UUID          NOT NULL REFERENCES branches(id),
    inventory_item_id    UUID          NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    stock_location_id    UUID          REFERENCES stock_locations(id),
    alert_type           VARCHAR(32)   NOT NULL,
    severity             VARCHAR(16)   NOT NULL DEFAULT 'WARNING',
    message              VARCHAR(400)  NOT NULL,
    current_value        NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    threshold_value      NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    status               VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    dedup_key            VARCHAR(160)  NOT NULL,
    resolved_by_user_id  UUID          REFERENCES users(id),
    resolved_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_inventory_alerts_type CHECK (alert_type IN (
        'LOW_STOCK', 'OUT_OF_STOCK', 'EXPIRING_SOON', 'EXPIRED', 'UNUSUAL_VARIANCE', 'HIGH_WASTE'
    )),
    CONSTRAINT chk_inventory_alerts_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_inventory_alerts_status CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED'))
);

CREATE UNIQUE INDEX uq_inventory_alerts_active_dedup
    ON inventory_alerts (tenant_id, branch_id, dedup_key)
    WHERE status IN ('ACTIVE', 'ACKNOWLEDGED');
