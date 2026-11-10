-- Phase 4 / V010: Core POS Menu, Branch Category Visibility, Table Statuses, Order Lifecycle & Kitchen Flags

ALTER TABLE products
    ADD COLUMN display_order INT NOT NULL DEFAULT 0,
    ADD COLUMN product_type VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    ADD CONSTRAINT chk_products_type CHECK (product_type IN ('STANDARD', 'COMBO', 'MODIFIER_ONLY', 'SERVICE'));

ALTER TABLE modifiers
    ADD COLUMN default_selected BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE branch_category_overrides (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID        NOT NULL REFERENCES tenants(id),
    branch_id               UUID        NOT NULL REFERENCES branches(id),
    category_id             UUID        NOT NULL REFERENCES categories(id),
    visible                 BOOLEAN     NOT NULL DEFAULT TRUE,
    display_order_override  INT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                 BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_branch_category_override UNIQUE (tenant_id, branch_id, category_id)
);

CREATE INDEX idx_branch_category_overrides_lookup ON branch_category_overrides (tenant_id, branch_id, visible);

ALTER TABLE dining_tables
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

UPDATE dining_tables SET status = 'CLEANING' WHERE status = 'DIRTY';
UPDATE dining_tables SET status = 'OUT_OF_SERVICE' WHERE status = 'DISABLED';

ALTER TABLE dining_tables
    DROP CONSTRAINT chk_dining_tables_status,
    ADD CONSTRAINT chk_dining_tables_status CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'RESERVED', 'CLEANING', 'OUT_OF_SERVICE'));

ALTER TABLE orders
    ADD COLUMN assigned_waiter_id UUID REFERENCES users(id),
    ADD COLUMN customer_reference VARCHAR(200),
    ADD COLUMN held BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sent_to_kitchen_at TIMESTAMPTZ;

ALTER TABLE orders
    DROP CONSTRAINT chk_orders_status,
    ADD CONSTRAINT chk_orders_status CHECK (status IN ('DRAFT', 'OPEN', 'ON_HOLD', 'IN_KITCHEN', 'READY', 'SERVED', 'PAID', 'CANCELLED', 'REFUNDED'));

ALTER TABLE order_items
    ADD COLUMN tax_id UUID REFERENCES taxes(id) ON DELETE RESTRICT,
    ADD COLUMN tax_name_snapshot VARCHAR(120),
    ADD COLUMN tax_inclusive BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sent_to_kitchen BOOLEAN NOT NULL DEFAULT FALSE;
