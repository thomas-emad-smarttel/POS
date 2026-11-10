-- Phase 2 / V002: Complete Core & Tenancy (server_nodes), Identity (user_roles), and Append-Only Audit Protection

CREATE TABLE server_nodes (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    branch_id       UUID         NOT NULL REFERENCES branches(id),
    node_code       VARCHAR(64)  NOT NULL,
    hostname        VARCHAR(255) NOT NULL,
    base_url        VARCHAR(500) NOT NULL,
    node_role       VARCHAR(32)  NOT NULL DEFAULT 'PRIMARY',
    software_version VARCHAR(64) NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ONLINE',
    last_heartbeat_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_server_nodes_branch_code UNIQUE (tenant_id, branch_id, node_code),
    CONSTRAINT chk_server_nodes_role CHECK (node_role IN ('PRIMARY', 'STANDBY')),
    CONSTRAINT chk_server_nodes_status CHECK (status IN ('ONLINE', 'DEGRADED', 'OFFLINE', 'RETIRED'))
);

CREATE INDEX idx_server_nodes_tenant_branch ON server_nodes (tenant_id, branch_id);

-- Tenant-level direct user-role membership (branch-scoped overrides live in user_role_assignments)
CREATE TABLE user_roles (
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id         UUID        NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_tenant_role ON user_roles (tenant_id, role_id);

-- Shared trigger function to guarantee append-only immutability on financial, inventory, and audit ledgers
CREATE OR REPLACE FUNCTION prevent_append_only_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only and cannot be %d', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_mutation();
