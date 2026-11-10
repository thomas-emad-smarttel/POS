-- Phase 2 / V006: Kitchen Stations, Kitchen Tickets, Items, and Append-Only Events

CREATE TABLE kitchen_stations (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    branch_id       UUID         NOT NULL REFERENCES branches(id),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    printer_device_id UUID,
    display_order   INT          NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_kitchen_stations_branch_code UNIQUE (tenant_id, branch_id, code)
);

CREATE TABLE kitchen_tickets (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL REFERENCES tenants(id),
    branch_id           UUID         NOT NULL REFERENCES branches(id),
    order_id            UUID         NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    kitchen_station_id  UUID         NOT NULL REFERENCES kitchen_stations(id),
    ticket_number       VARCHAR(64)  NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'NEW',
    priority            INT          NOT NULL DEFAULT 0,
    fired_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    started_at          TIMESTAMPTZ,
    ready_at            TIMESTAMPTZ,
    bumped_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_kitchen_tickets_branch_num UNIQUE (tenant_id, branch_id, ticket_number),
    CONSTRAINT chk_kitchen_tickets_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'READY', 'BUMPED', 'CANCELLED'))
);

CREATE INDEX idx_kitchen_tickets_station_status ON kitchen_tickets (tenant_id, branch_id, kitchen_station_id, status, fired_at);

CREATE TABLE kitchen_ticket_items (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    branch_id           UUID          NOT NULL REFERENCES branches(id),
    kitchen_ticket_id   UUID          NOT NULL REFERENCES kitchen_tickets(id) ON DELETE RESTRICT,
    order_item_id       UUID          NOT NULL REFERENCES order_items(id) ON DELETE RESTRICT,
    item_name_snapshot  VARCHAR(200)  NOT NULL,
    quantity            NUMERIC(14,4) NOT NULL,
    notes               VARCHAR(500),
    status              VARCHAR(32)   NOT NULL DEFAULT 'NEW',
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_kitchen_ticket_items_qty CHECK (quantity > 0.0000),
    CONSTRAINT chk_kitchen_ticket_items_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'READY', 'VOIDED'))
);

CREATE INDEX idx_kitchen_ticket_items_ticket ON kitchen_ticket_items (kitchen_ticket_id);

CREATE TABLE kitchen_ticket_events (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL REFERENCES tenants(id),
    branch_id           UUID         NOT NULL REFERENCES branches(id),
    kitchen_ticket_id   UUID         NOT NULL REFERENCES kitchen_tickets(id) ON DELETE RESTRICT,
    actor_user_id       UUID         REFERENCES users(id),
    event_type          VARCHAR(32)  NOT NULL,
    previous_status     VARCHAR(32),
    new_status          VARCHAR(32)  NOT NULL,
    occurred_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_kitchen_event_type CHECK (event_type IN ('FIRED', 'STARTED', 'READY', 'BUMPED', 'RECALLED', 'CANCELLED'))
);

CREATE INDEX idx_kitchen_ticket_events_ticket ON kitchen_ticket_events (kitchen_ticket_id, occurred_at);

CREATE TRIGGER trg_kitchen_ticket_events_immutable
    BEFORE UPDATE OR DELETE ON kitchen_ticket_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_mutation();
