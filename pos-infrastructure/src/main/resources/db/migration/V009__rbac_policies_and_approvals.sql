-- Phase 3 / V009: Role Scope & Activation, Sensitive Permission Metadata, and Supervisor Approvals

ALTER TABLE roles
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN scope_type VARCHAR(24) NOT NULL DEFAULT 'BRANCH',
    ADD CONSTRAINT chk_roles_scope_type CHECK (scope_type IN ('TENANT', 'BRANCH'));

ALTER TABLE permissions
    ADD COLUMN sensitive BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE supervisor_approvals (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID         NOT NULL REFERENCES tenants(id),
    branch_id               UUID         NOT NULL REFERENCES branches(id),
    terminal_id             UUID         REFERENCES terminals(id),
    required_permission     VARCHAR(100) NOT NULL,
    target_entity_type      VARCHAR(80)  NOT NULL,
    target_entity_id        VARCHAR(120) NOT NULL,
    requested_by_user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    approved_by_user_id     UUID         REFERENCES users(id) ON DELETE RESTRICT,
    status                  VARCHAR(24)  NOT NULL,
    reason                  VARCHAR(300) NOT NULL,
    correlation_id          VARCHAR(64)  NOT NULL,
    expires_at              TIMESTAMPTZ  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_supervisor_approvals_status CHECK (status IN ('APPROVED', 'REJECTED')),
    CONSTRAINT chk_supervisor_no_self_approve CHECK (
        approved_by_user_id IS NULL OR approved_by_user_id <> requested_by_user_id
    )
);

CREATE INDEX idx_supervisor_approvals_branch_time ON supervisor_approvals (tenant_id, branch_id, created_at DESC);

CREATE TRIGGER trg_supervisor_approvals_immutable
    BEFORE UPDATE OR DELETE ON supervisor_approvals
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_mutation();
