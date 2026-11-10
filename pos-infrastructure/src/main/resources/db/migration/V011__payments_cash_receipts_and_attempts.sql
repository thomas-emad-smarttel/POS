-- Phase 5 / V011: Payment Attempts, Reopenable Cash Sessions, Extended Cash Movements, Append-Only Drawer Events & Receipts

ALTER TABLE orders
    DROP CONSTRAINT chk_orders_status,
    ADD CONSTRAINT chk_orders_status CHECK (status IN (
        'DRAFT', 'OPEN', 'ON_HOLD', 'IN_KITCHEN', 'READY', 'SERVED', 'PAID', 'PARTIALLY_REFUNDED', 'CANCELLED', 'REFUNDED'
    ));

ALTER TABLE cash_sessions
    ADD COLUMN reopened_by_user_id UUID REFERENCES users(id),
    ADD COLUMN reopened_at TIMESTAMPTZ,
    ADD COLUMN reopen_reason VARCHAR(300),
    DROP CONSTRAINT chk_cash_sessions_status,
    ADD CONSTRAINT chk_cash_sessions_status CHECK (status IN ('OPEN', 'CLOSED', 'RECONCILED', 'REOPENED'));

CREATE UNIQUE INDEX uq_cash_sessions_active_terminal
    ON cash_sessions (tenant_id, branch_id, terminal_id)
    WHERE status IN ('OPEN', 'REOPENED');

ALTER TABLE cash_movements
    ADD COLUMN idempotency_key VARCHAR(128),
    DROP CONSTRAINT chk_cash_movements_type,
    ADD CONSTRAINT chk_cash_movements_type CHECK (movement_type IN (
        'OPENING_FLOAT', 'CASH_SALE', 'CASH_REFUND', 'PAY_IN', 'PAY_OUT', 'WITHDRAWAL', 'DEPOSIT', 'SAFE_DROP', 'DRAWER_NO_SALE'
    ));

CREATE UNIQUE INDEX uq_cash_movements_idempotency
    ON cash_movements (tenant_id, branch_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE payment_attempts (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID          NOT NULL REFERENCES tenants(id),
    branch_id               UUID          NOT NULL REFERENCES branches(id),
    order_id                UUID          NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    payment_method_id       UUID          NOT NULL REFERENCES payment_methods(id) ON DELETE RESTRICT,
    cash_session_id         UUID          REFERENCES cash_sessions(id) ON DELETE RESTRICT,
    initiated_by_user_id    UUID          NOT NULL REFERENCES users(id),
    linked_payment_id       UUID          REFERENCES payments(id) ON DELETE RESTRICT,
    idempotency_key         VARCHAR(128)  NOT NULL,
    currency_code           CHAR(3)       NOT NULL,
    requested_amount        NUMERIC(14,4) NOT NULL,
    tendered_amount         NUMERIC(14,4) NOT NULL,
    change_amount           NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    tip_amount              NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    status                  VARCHAR(32)   NOT NULL,
    provider_name           VARCHAR(64)   NOT NULL,
    provider_tx_id          VARCHAR(128),
    terminal_ref            VARCHAR(64),
    masked_pan              VARCHAR(32),
    card_brand              VARCHAR(32),
    auth_code               VARCHAR(64),
    response_code           VARCHAR(32),
    failure_reason          VARCHAR(300),
    reconciled              BOOLEAN       NOT NULL DEFAULT FALSE,
    reconciled_at           TIMESTAMPTZ,
    reconciled_by_user_id   UUID          REFERENCES users(id),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_attempts_idempotency UNIQUE (tenant_id, branch_id, idempotency_key),
    CONSTRAINT chk_payment_attempts_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'UNCERTAIN', 'PARTIALLY_REFUNDED', 'REFUNDED'
    )),
    CONSTRAINT chk_payment_attempts_amounts CHECK (
        requested_amount > 0.0000 AND tendered_amount >= 0.0000 AND change_amount >= 0.0000 AND tip_amount >= 0.0000
    )
);

CREATE INDEX idx_payment_attempts_order ON payment_attempts (order_id, created_at);
CREATE INDEX idx_payment_attempts_branch_status ON payment_attempts (tenant_id, branch_id, status);

ALTER TABLE payment_transactions
    ALTER COLUMN payment_id DROP NOT NULL,
    ADD COLUMN payment_attempt_id UUID REFERENCES payment_attempts(id) ON DELETE RESTRICT,
    DROP CONSTRAINT chk_payment_tx_type,
    ADD CONSTRAINT chk_payment_tx_type CHECK (transaction_type IN (
        'START', 'AUTHORIZE', 'CAPTURE', 'SETTLE', 'DECLINE', 'QUERY', 'CANCEL', 'REVERSE', 'TIMEOUT', 'RECONCILE'
    ));

ALTER TABLE refunds
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN failure_reason VARCHAR(300),
    ADD CONSTRAINT chk_refunds_status CHECK (status IN ('COMPLETED', 'FAILED'));

CREATE TABLE drawer_events (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID         NOT NULL REFERENCES tenants(id),
    branch_id               UUID         NOT NULL REFERENCES branches(id),
    terminal_id             UUID         REFERENCES terminals(id),
    cash_session_id         UUID         REFERENCES cash_sessions(id) ON DELETE RESTRICT,
    order_id                UUID         REFERENCES orders(id) ON DELETE RESTRICT,
    payment_id              UUID         REFERENCES payments(id) ON DELETE RESTRICT,
    actor_user_id           UUID         NOT NULL REFERENCES users(id),
    approval_id             UUID         REFERENCES supervisor_approvals(id) ON DELETE RESTRICT,
    trigger_type            VARCHAR(32)  NOT NULL,
    reason                  VARCHAR(300) NOT NULL,
    hardware_device_name    VARCHAR(120) NOT NULL,
    hardware_status         VARCHAR(32)  NOT NULL,
    opened_ok               BOOLEAN      NOT NULL,
    error_message           VARCHAR(300),
    correlation_id          VARCHAR(64)  NOT NULL,
    occurred_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_drawer_events_trigger CHECK (trigger_type IN ('CASH_PAYMENT', 'MANUAL_NO_SALE', 'CASH_SESSION_ACTION'))
);

CREATE INDEX idx_drawer_events_branch_time ON drawer_events (tenant_id, branch_id, occurred_at DESC);

CREATE TRIGGER trg_drawer_events_immutable
    BEFORE UPDATE OR DELETE ON drawer_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_mutation();

CREATE TABLE receipts (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID         NOT NULL REFERENCES tenants(id),
    branch_id               UUID         NOT NULL REFERENCES branches(id),
    order_id                UUID         REFERENCES orders(id) ON DELETE RESTRICT,
    payment_id              UUID         REFERENCES payments(id) ON DELETE RESTRICT,
    refund_id               UUID         REFERENCES refunds(id) ON DELETE RESTRICT,
    cash_session_id         UUID         REFERENCES cash_sessions(id) ON DELETE RESTRICT,
    receipt_number          VARCHAR(64)  NOT NULL,
    receipt_type            VARCHAR(32)  NOT NULL,
    language                VARCHAR(16)  NOT NULL DEFAULT 'BILINGUAL',
    paper_width             INT          NOT NULL DEFAULT 42,
    encoding                VARCHAR(32)  NOT NULL DEFAULT 'UTF-8',
    rendered_text           TEXT         NOT NULL,
    escpos_base64           TEXT         NOT NULL,
    print_count             INT          NOT NULL DEFAULT 0,
    last_print_status       VARCHAR(32)  NOT NULL,
    last_print_error        VARCHAR(300),
    last_printed_at         TIMESTAMPTZ,
    created_by_user_id      UUID         NOT NULL REFERENCES users(id),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_receipts_branch_number UNIQUE (tenant_id, branch_id, receipt_number),
    CONSTRAINT chk_receipts_type CHECK (receipt_type IN (
        'CUSTOMER', 'DUPLICATE', 'REFUND', 'PAYMENT_CONFIRMATION', 'CASH_SESSION_REPORT'
    )),
    CONSTRAINT chk_receipts_width CHECK (paper_width BETWEEN 32 AND 64),
    CONSTRAINT chk_receipts_print_count CHECK (print_count >= 0)
);

CREATE INDEX idx_receipts_order ON receipts (tenant_id, branch_id, order_id, created_at DESC);
CREATE INDEX idx_receipts_cash_session ON receipts (cash_session_id) WHERE cash_session_id IS NOT NULL;
