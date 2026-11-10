package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "supervisor_approvals")
public class SupervisorApprovalEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "terminal_id", updatable = false)
    private UUID terminalId;

    @Column(name = "required_permission", nullable = false, updatable = false, length = 100)
    private String requiredPermission;

    @Column(name = "target_entity_type", nullable = false, updatable = false, length = 80)
    private String targetEntityType;

    @Column(name = "target_entity_id", nullable = false, updatable = false, length = 120)
    private String targetEntityId;

    @Column(name = "requested_by_user_id", nullable = false, updatable = false)
    private UUID requestedByUserId;

    @Column(name = "approved_by_user_id", updatable = false)
    private UUID approvedByUserId;

    @Column(name = "status", nullable = false, updatable = false, length = 24)
    private String status;

    @Column(name = "reason", nullable = false, updatable = false, length = 300)
    private String reason;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 64)
    private String correlationId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SupervisorApprovalEntity() {
    }

    public SupervisorApprovalEntity(
            UUID id,
            UUID tenantId,
            UUID branchId,
            UUID terminalId,
            String requiredPermission,
            String targetEntityType,
            String targetEntityId,
            UUID requestedByUserId,
            UUID approvedByUserId,
            String status,
            String reason,
            String correlationId,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.terminalId = terminalId;
        this.requiredPermission = requiredPermission;
        this.targetEntityType = targetEntityType;
        this.targetEntityId = targetEntityId;
        this.requestedByUserId = requestedByUserId;
        this.approvedByUserId = approvedByUserId;
        this.status = status;
        this.reason = reason;
        this.correlationId = correlationId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getTerminalId() { return terminalId; }
    public String getRequiredPermission() { return requiredPermission; }
    public String getTargetEntityType() { return targetEntityType; }
    public String getTargetEntityId() { return targetEntityId; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public String getCorrelationId() { return correlationId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
