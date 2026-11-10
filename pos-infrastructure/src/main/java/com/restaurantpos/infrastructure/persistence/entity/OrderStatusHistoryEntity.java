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
@Table(name = "order_status_history")
public class OrderStatusHistoryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "from_status", updatable = false, length = 32)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, updatable = false, length = 32)
    private String toStatus;

    @Column(name = "changed_by_user_id", nullable = false, updatable = false)
    private UUID changedByUserId;

    @Column(name = "reason", updatable = false, length = 300)
    private String reason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected OrderStatusHistoryEntity() {
    }

    public OrderStatusHistoryEntity(
            UUID id,
            UUID tenantId,
            UUID branchId,
            UUID orderId,
            String fromStatus,
            String toStatus,
            UUID changedByUserId,
            String reason,
            Instant changedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByUserId = changedByUserId;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public UUID getId() { return id; }
    public String getToStatus() { return toStatus; }
}
