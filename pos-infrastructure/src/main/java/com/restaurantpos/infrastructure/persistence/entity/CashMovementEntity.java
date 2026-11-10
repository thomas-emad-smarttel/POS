package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "cash_movements")
public class CashMovementEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "cash_session_id", nullable = false, updatable = false)
    private UUID cashSessionId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "movement_type", nullable = false, updatable = false, length = 32)
    private String movementType;

    @Column(name = "amount", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, updatable = false, length = 300)
    private String reason;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 64)
    private String correlationId;

    @Column(name = "idempotency_key", updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected CashMovementEntity() {
    }

    public CashMovementEntity(
            UUID id, UUID tenantId, UUID branchId, UUID cashSessionId, UUID actorUserId,
            String movementType, BigDecimal amount, String reason,
            String correlationId, String idempotencyKey, Instant occurredAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.cashSessionId = cashSessionId;
        this.actorUserId = actorUserId;
        this.movementType = movementType;
        this.amount = amount;
        this.reason = reason;
        this.correlationId = correlationId;
        this.idempotencyKey = idempotencyKey;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getCashSessionId() { return cashSessionId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getMovementType() { return movementType; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public String getCorrelationId() { return correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getOccurredAt() { return occurredAt; }
}
