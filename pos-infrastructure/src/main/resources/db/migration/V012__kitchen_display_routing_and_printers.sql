-- Phase 6 / V012: Kitchen Stations Extensions, Routing Rules, Display Config, Ticket Snapshots, Outbox Idempotency, Unrouted Items & Print Jobs

ALTER TABLE kitchen_stations
    ADD COLUMN station_type          VARCHAR(32) NOT NULL DEFAULT 'HOT_KITCHEN',
    ADD COLUMN expected_prep_minutes INT         NOT NULL DEFAULT 10,
    ADD COLUMN expo_enabled          BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN paper_width           INT         NOT NULL DEFAULT 42,
    ADD COLUMN receipt_language      VARCHAR(16) NOT NULL DEFAULT 'BILINGUAL',
    ADD COLUMN receipt_encoding      VARCHAR(32) NOT NULL DEFAULT 'UTF-8',
    ADD COLUMN default_station       BOOLEAN     NOT NULL DEFAULT FALSE;

CREATE TABLE kitchen_station_assignments (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    branch_id           UUID        NOT NULL REFERENCES branches(id),
    kitchen_station_id  UUID        NOT NULL REFERENCES kitchen_stations(id),
    user_id             UUID        REFERENCES users(id),
    terminal_id         UUID        REFERENCES terminals(id),
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kitchen_assignments_station ON kitchen_station_assignments (tenant_id, branch_id, kitchen_station_id, active);

CREATE TABLE kitchen_routing_rules (
    id                              UUID PRIMARY KEY,
    tenant_id                       UUID        NOT NULL REFERENCES tenants(id),
    branch_id                       UUID        NOT NULL REFERENCES branches(id),
    kitchen_station_id              UUID        NOT NULL REFERENCES kitchen_stations(id),
    product_id                      UUID        REFERENCES products(id) ON DELETE RESTRICT,
    category_id                     UUID        REFERENCES categories(id) ON DELETE RESTRICT,
    precedence                      INT         NOT NULL DEFAULT 100,
    expected_prep_minutes_override  INT,
    active                          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                         BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_kitchen_routing_lookup ON kitchen_routing_rules (tenant_id, branch_id, active, precedence);

CREATE TABLE kitchen_display_configs (
    id                              UUID PRIMARY KEY,
    tenant_id                       UUID        NOT NULL REFERENCES tenants(id),
    branch_id                       UUID        NOT NULL REFERENCES branches(id),
    expo_enabled                    BOOLEAN     NOT NULL DEFAULT TRUE,
    default_sort_mode               VARCHAR(32) NOT NULL DEFAULT 'FIFO',
    default_expected_prep_minutes   INT         NOT NULL DEFAULT 10,
    auto_print_on_dispatch          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_kitchen_display_configs_branch UNIQUE (tenant_id, branch_id)
);

ALTER TABLE kitchen_tickets
    ADD COLUMN order_number_snapshot   VARCHAR(64) NOT NULL DEFAULT 'ORD',
    ADD COLUMN order_type_snapshot     VARCHAR(32) NOT NULL DEFAULT 'DINE_IN',
    ADD COLUMN table_label_snapshot    VARCHAR(100),
    ADD COLUMN waiter_id               UUID,
    ADD COLUMN waiter_name_snapshot    VARCHAR(120),
    ADD COLUMN source_outbox_event_id  UUID,
    ADD COLUMN ticket_kind             VARCHAR(32) NOT NULL DEFAULT 'INITIAL',
    ADD COLUMN priority_level          VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN expected_prep_minutes   INT         NOT NULL DEFAULT 10,
    ADD COLUMN accepted_at             TIMESTAMPTZ,
    ADD COLUMN cancelled_at            TIMESTAMPTZ,
    ADD COLUMN cancelled_by_user_id    UUID,
    ADD COLUMN hold_reason             VARCHAR(300),
    ADD COLUMN cancel_reason           VARCHAR(300),
    DROP CONSTRAINT chk_kitchen_tickets_status,
    ADD CONSTRAINT chk_kitchen_tickets_status CHECK (status IN (
        'NEW', 'QUEUED', 'ACCEPTED', 'IN_PROGRESS', 'PREPARING', 'READY', 'SERVED', 'BUMPED', 'CANCELLED', 'ON_HOLD'
    ));

CREATE INDEX idx_kitchen_tickets_order ON kitchen_tickets (tenant_id, branch_id, order_id);

ALTER TABLE kitchen_ticket_items
    ADD COLUMN variant_name_snapshot    VARCHAR(150),
    ADD COLUMN modifiers_snapshot_json  TEXT,
    ADD COLUMN prep_instructions        VARCHAR(500),
    ADD COLUMN expected_prep_minutes    INT         NOT NULL DEFAULT 10,
    ADD COLUMN started_at               TIMESTAMPTZ,
    ADD COLUMN ready_at                 TIMESTAMPTZ,
    ADD COLUMN served_at                TIMESTAMPTZ,
    ADD COLUMN cancel_reason            VARCHAR(300),
    DROP CONSTRAINT chk_kitchen_ticket_items_status,
    ADD CONSTRAINT chk_kitchen_ticket_items_status CHECK (status IN (
        'NEW', 'QUEUED', 'PREPARING', 'IN_PROGRESS', 'READY', 'SERVED', 'CANCELLED', 'VOIDED'
    ));

ALTER TABLE kitchen_ticket_events
    ADD COLUMN kitchen_ticket_item_id  UUID REFERENCES kitchen_ticket_items(id) ON DELETE RESTRICT,
    ADD COLUMN reason                  VARCHAR(300),
    ADD COLUMN details_json            TEXT,
    DROP CONSTRAINT chk_kitchen_event_type,
    ADD CONSTRAINT chk_kitchen_event_type CHECK (event_type IN (
        'FIRED', 'QUEUED', 'ACCEPTED', 'STARTED', 'READY', 'SERVED', 'BUMPED', 'RECALLED',
        'HELD', 'RELEASED', 'CANCELLED', 'REFIRED', 'ITEM_READY', 'ITEM_CANCELLED', 'NOTE_UPDATED'
    ));

CREATE TABLE kitchen_processed_events (
    outbox_event_id UUID PRIMARY KEY REFERENCES outbox_events(id) ON DELETE RESTRICT,
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    branch_id       UUID        REFERENCES branches(id),
    event_type      VARCHAR(80) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE kitchen_unrouted_items (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID          NOT NULL REFERENCES tenants(id),
    branch_id               UUID          NOT NULL REFERENCES branches(id),
    outbox_event_id         UUID          NOT NULL REFERENCES outbox_events(id) ON DELETE RESTRICT,
    order_id                UUID          NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    order_number            VARCHAR(64)   NOT NULL,
    order_item_id           UUID          NOT NULL REFERENCES order_items(id) ON DELETE RESTRICT,
    product_id              UUID          REFERENCES products(id) ON DELETE RESTRICT,
    product_name_snapshot   VARCHAR(200)  NOT NULL,
    quantity                NUMERIC(14,4) NOT NULL,
    modifiers_summary       VARCHAR(500),
    status                  VARCHAR(32)   NOT NULL DEFAULT 'OPEN',
    resolved_station_id     UUID          REFERENCES kitchen_stations(id),
    resolved_by_user_id     UUID          REFERENCES users(id),
    resolved_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_kitchen_unrouted_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
);

CREATE INDEX idx_kitchen_unrouted_open ON kitchen_unrouted_items (tenant_id, branch_id, status, created_at);

CREATE TABLE kitchen_print_jobs (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL REFERENCES tenants(id),
    branch_id           UUID         NOT NULL REFERENCES branches(id),
    kitchen_ticket_id   UUID         NOT NULL REFERENCES kitchen_tickets(id) ON DELETE RESTRICT,
    kitchen_station_id  UUID         NOT NULL REFERENCES kitchen_stations(id),
    job_type            VARCHAR(32)  NOT NULL,
    printer_name        VARCHAR(120) NOT NULL,
    language            VARCHAR(16)  NOT NULL DEFAULT 'BILINGUAL',
    paper_width         INT          NOT NULL DEFAULT 42,
    encoding            VARCHAR(32)  NOT NULL DEFAULT 'UTF-8',
    rendered_text       TEXT         NOT NULL,
    escpos_base64       TEXT         NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    attempt_count       INT          NOT NULL DEFAULT 1,
    error_message       VARCHAR(300),
    idempotency_key     VARCHAR(128) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_attempt_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_kitchen_print_jobs_idemp UNIQUE (tenant_id, branch_id, idempotency_key)
);

CREATE INDEX idx_kitchen_print_jobs_ticket ON kitchen_print_jobs (kitchen_ticket_id, created_at);
CREATE INDEX idx_kitchen_print_jobs_branch_status ON kitchen_print_jobs (tenant_id, branch_id, status);
