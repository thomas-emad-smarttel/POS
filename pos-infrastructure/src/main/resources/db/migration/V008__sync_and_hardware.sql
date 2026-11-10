-- Phase 2 / V008: Offline-First Synchronization (Outbox/Inbox/Cursors/Conflicts/DeadLetters) & Hardware Devices

CREATE TABLE hardware_devices (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    branch_id       UUID         NOT NULL REFERENCES branches(id),
    terminal_id     UUID         REFERENCES terminals(id),
    device_code     VARCHAR(64)  NOT NULL,
    device_name     VARCHAR(120) NOT NULL,
    device_type     VARCHAR(32)  NOT NULL,
    transport_type  VARCHAR(32)  NOT NULL,
    connection_uri  VARCHAR(300) NOT NULL,
    driver_profile  VARCHAR(64)  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_hardware_devices_branch_code UNIQUE (tenant_id, branch_id, device_code),
    CONSTRAINT chk_hardware_device_type CHECK (device_type IN ('RECEIPT_PRINTER', 'KITCHEN_PRINTER', 'CASH_DRAWER', 'BARCODE_SCANNER', 'WEIGHING_SCALE')),
    CONSTRAINT chk_hardware_transport CHECK (transport_type IN ('TCP_IP', 'USB', 'SERIAL_RS232', 'USB_HID', 'VIRTUAL'))
);

CREATE INDEX idx_hardware_devices_branch ON hardware_devices (tenant_id, branch_id, active);

CREATE TABLE sync_devices (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    branch_id       UUID         NOT NULL REFERENCES branches(id),
    device_code     VARCHAR(64)  NOT NULL,
    device_kind     VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    last_sync_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_sync_devices_branch_code UNIQUE (tenant_id, branch_id, device_code),
    CONSTRAINT chk_sync_devices_kind CHECK (device_kind IN ('LOCAL_SERVER', 'POS_TERMINAL', 'CLOUD_NODE')),
    CONSTRAINT chk_sync_devices_status CHECK (status IN ('ACTIVE', 'PAUSED', 'OFFLINE', 'DECOMMISSIONED'))
);

CREATE TABLE sync_cursors (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    branch_id       UUID         NOT NULL REFERENCES branches(id),
    sync_device_id  UUID         NOT NULL REFERENCES sync_devices(id),
    stream_name     VARCHAR(64)  NOT NULL,
    cursor_value    BIGINT       NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_sync_cursors_device_stream UNIQUE (sync_device_id, stream_name),
    CONSTRAINT chk_sync_cursors_non_neg CHECK (cursor_value >= 0)
);

CREATE TABLE sync_runs (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    branch_id       UUID         NOT NULL REFERENCES branches(id),
    sync_device_id  UUID         NOT NULL REFERENCES sync_devices(id),
    direction       VARCHAR(16)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    events_processed INT         NOT NULL DEFAULT 0,
    error_summary   VARCHAR(1000),
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    CONSTRAINT chk_sync_runs_direction CHECK (direction IN ('PUSH', 'PULL')),
    CONSTRAINT chk_sync_runs_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED'))
);

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    branch_id       UUID         REFERENCES branches(id),
    aggregate_type  VARCHAR(80)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_json    JSONB        NOT NULL,
    sequence_num    BIGINT GENERATED ALWAYS AS IDENTITY,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_outbox_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'IN_FLIGHT', 'PUBLISHED', 'DEAD_LETTER')),
    CONSTRAINT chk_outbox_retry CHECK (retry_count >= 0)
);

CREATE INDEX idx_outbox_pending_dispatch ON outbox_events (status, next_attempt_at, sequence_num)
    WHERE status IN ('PENDING', 'IN_FLIGHT');

CREATE TABLE inbox_events (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    branch_id       UUID         REFERENCES branches(id),
    source_node_id  VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    payload_json    JSONB        NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    CONSTRAINT uq_inbox_source_idempotency UNIQUE (tenant_id, source_node_id, idempotency_key)
);

CREATE TABLE sync_conflicts (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL REFERENCES tenants(id),
    branch_id           UUID         NOT NULL REFERENCES branches(id),
    entity_type         VARCHAR(80)  NOT NULL,
    entity_id           UUID         NOT NULL,
    resolution_policy   VARCHAR(64)  NOT NULL,
    resolution_status   VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    local_payload_json  JSONB        NOT NULL,
    remote_payload_json JSONB        NOT NULL,
    resolved_by_user_id UUID         REFERENCES users(id),
    detected_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ,
    CONSTRAINT chk_sync_conflicts_status CHECK (resolution_status IN ('OPEN', 'AUTO_RESOLVED', 'MANUAL_RESOLVED', 'IGNORED'))
);

CREATE INDEX idx_sync_conflicts_branch_status ON sync_conflicts (tenant_id, branch_id, resolution_status);

CREATE TABLE sync_dead_letters (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL REFERENCES tenants(id),
    branch_id           UUID         REFERENCES branches(id),
    outbox_event_id     UUID         REFERENCES outbox_events(id),
    event_type          VARCHAR(80)  NOT NULL,
    payload_json        JSONB        NOT NULL,
    failure_reason      VARCHAR(1000) NOT NULL,
    retry_attempts      INT          NOT NULL,
    quarantined_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    requeued_at         TIMESTAMPTZ
);

CREATE INDEX idx_sync_dead_letters_tenant ON sync_dead_letters (tenant_id, quarantined_at DESC);
