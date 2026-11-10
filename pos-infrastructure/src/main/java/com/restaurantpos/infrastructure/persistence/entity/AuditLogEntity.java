package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", updatable = false)
    private UUID branchId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "action", nullable = false, updatable = false, length = 64)
    private String action;

    @Column(name = "target_entity_type", updatable = false, length = 80)
    private String targetEntityType;

    @Column(name = "target_entity_id", updatable = false, length = 120)
    private String targetEntityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", updatable = false, columnDefinition = "jsonb")
    private String detailsJson;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 64)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditLogEntity() {
    }

    public AuditLogEntity(
            UUID id,
            UUID tenantId,
            UUID branchId,
            UUID actorUserId,
            String action,
            String targetEntityType,
            String targetEntityId,
            String detailsJson,
            String correlationId,
            Instant occurredAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetEntityType = targetEntityType;
        this.targetEntityId = targetEntityId;
        this.detailsJson = detailsJson;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getTargetEntityType() { return targetEntityType; }
    public String getTargetEntityId() { return targetEntityId; }
    public String getDetailsJson() { return detailsJson; }
    public String getCorrelationId() { return correlationId; }
    public Instant getOccurredAt() { return occurredAt; }
}
