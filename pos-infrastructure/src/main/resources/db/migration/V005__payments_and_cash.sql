-- Phase 2 / V005: Payment Methods, Cash Sessions, Cash Movements, Append-Only Payments & Refunds

CREATE TABLE payment_methods (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    method_type     VARCHAR(32)  NOT NULL,
    opens_drawer    BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_methods_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_payment_methods_type CHECK (method_type IN ('CASH', 'CARD', 'DIGITAL_WALLET', 'VOUCHER', 'HOUSE_ACCOUNT'))
);

CREATE TABLE cash_sessions (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID          NOT NULL REFERENCES tenants(id),
    branch_id               UUID          NOT NULL REFERENCES branches(id),
    terminal_id             UUID          NOT NULL REFERENCES terminals(id),
    opened_by_user_id       UUID          NOT NULL REFERENCES users(id),
    closed_by_user_id       UUID          REFERENCES users(id),
    currency_code           CHAR(3)       NOT NULL,
    opening_float           NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    expected_cash_amount    NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    counted_cash_amount     NUMERIC(14,4),
    variance_amount         NUMERIC(14,4),
    status                  VARCHAR(32)   NOT NULL DEFAULT 'OPEN',
    opened_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    closed_at               TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_cash_sessions_status CHECK (status IN ('OPEN', 'CLOSED', 'RECONCILED')),
    CONSTRAINT chk_cash_sessions_opening CHECK (opening_float >= 0.0000)
);

CREATE INDEX idx_cash_sessions_branch_terminal_status ON cash_sessions (tenant_id, branch_id, terminal_id, status);

CREATE TABLE cash_movements (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    branch_id           UUID          NOT NULL REFERENCES branches(id),
    cash_session_id     UUID          NOT NULL REFERENCES cash_sessions(id) ON DELETE RESTRICT,
    actor_user_id       UUID          NOT NULL REFERENCES users(id),
    movement_type       VARCHAR(32)   NOT NULL,
    amount              NUMERIC(14,4) NOT NULL,
    reason              VARCHAR(300)  NOT NULL,
    correlation_id      VARCHAR(64)   NOT NULL,
    occurred_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_cash_movements_type CHECK (movement_type IN ('OPENING_FLOAT', 'PAY_IN', 'PAY_OUT', 'SAFE_DROP', 'DRAWER_NO_SALE')),
    CONSTRAINT chk_cash_movements_amount CHECK (amount >= 0.0000)
);

CREATE INDEX idx_cash_movements_session ON cash_movements (cash_session_id, occurred_at);

CREATE TRIGGER trg_cash_movements_immutable
    BEFORE UPDATE OR DELETE ON cash_movements
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_mutation();

-- Append-only settled payment records. Never store PAN or CVV.
CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    branch_id           UUID          NOT NULL REFERENCES branches(id),
    order_id            UUID          NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    cash_session_id     UUID          REFERENCES cash_sessions(id) ON DELETE RESTRICT,
    payment_method_id   UUID          NOT NULL REFERENCES payment_methods(id) ON DELETE RESTRICT,
    processed_by_user_id UUID         NOT NULL REFERENCES users(id),
    idempotency_key     VARCHAR(128)  NOT NULL,
    currency_code       CHAR(3)       NOT NULL,
    amount              NUMERIC(14,4) NOT NULL,
    tendered_amount     NUMERIC(14,4) NOT NULL,
    change_amount       NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    tip_amount          NUMERIC(14,4) NOT NULL DEFAULT 0.0000,
    status              VARCHAR(32)   NOT NULL,
    external_reference  VARCHAR(128),
    masked_account      VARCHAR(32),
    paid_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payments_idempotency UNIQUE (tenant_id, branch_id, idempotency_key),
    CONSTRAINT chk_payments_status CHECK (status IN ('CAPTURED', 'SETTLED', 'FAILED')),
    CONSTRAINT chk_payments_amounts CHECK (
        amount > 0.0000 AND
        tendered_amount >= 0.0000 AND
        change_amount >= 0.0000 AND
        tip_amount >= 0.0000
    )
);

CREATE INDEX idx_payments_order ON payments (order_id);
CREATE INDEX idx_payments_branch_time ON payments (tenant_id, branch_id, paid_at DESC);

CREATE TRIGGER trg_payments_immutable
    BEFORE UPDATE OR DELETE ON payments
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_mutation();

-- Append-only gateway/terminal transaction audit ledger (never stores raw card PAN/CVV)
CREATE TABLE payment_transactions (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    branch_id           UUID          NOT NULL REFERENCES branches(id),
    payment_id          UUID          NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    transaction_type    VARCHAR(32)   NOT NULL,
    amount              NUMERIC(14,4) NOT NULL,
    gateway_name        VARCHAR(64)   NOT NULL,
    gateway_tx_ref      VARCHAR(128),
    auth_code           VARCHAR(64),
    response_code       VARCHAR(32),
    occurred_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_tx_type CHECK (transaction_type IN ('AUTHORIZE', 'CAPTURE', 'SETTLE', 'DECLINE')),
    CONSTRAINT chk_payment_tx_amount CHECK (amount >= 0.0000)
);

CREATE INDEX idx_payment_transactions_payment ON payment_transactions (payment_id, occurred_at);

CREATE TRIGGER trg_payment_transactions_immutable
    BEFORE UPDATE OR DELETE ON payment_transactions
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_mutation();

-- Separate append-only refund transactions
CREATE TABLE refunds (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES tenants(id),
    branch_id           UUID          NOT NULL REFERENCES branches(id),
    order_id            UUID          NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    original_payment_id UUID          NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    issued_by_user_id   UUID          NOT NULL REFERENCES users(id),
    approved_by_user_id UUID          REFERENCES users(id),
    idempotency_key     VARCHAR(128)  NOT NULL,
    currency_code       CHAR(3)       NOT NULL,
    amount              NUMERIC(14,4) NOT NULL,
    reason              VARCHAR(300)  NOT NULL,
    external_reference  VARCHAR(128),
    refunded_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_refunds_idempotency UNIQUE (tenant_id, branch_id, idempotency_key),
    CONSTRAINT chk_refunds_amount_pos CHECK (amount > 0.0000)
);

CREATE INDEX idx_refunds_order ON refunds (order_id);
CREATE INDEX idx_refunds_payment ON refunds (original_payment_id);

CREATE TRIGGER trg_refunds_immutable
    BEFORE UPDATE OR DELETE ON refunds
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_mutation();
