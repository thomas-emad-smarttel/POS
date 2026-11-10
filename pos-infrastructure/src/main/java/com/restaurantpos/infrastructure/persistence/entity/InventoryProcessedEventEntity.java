package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_processed_events")
public class InventoryProcessedEventEntity {

    @Id
    @Column(name = "outbox_event_id", nullable = false, updatable = false)
    private UUID outboxEventId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", updatable = false)
    private UUID branchId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 80)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected InventoryProcessedEventEntity() {
    }

    public InventoryProcessedEventEntity(UUID outboxEventId, UUID tenantId, UUID branchId, String eventType, Instant processedAt) {
        this.outboxEventId = outboxEventId;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    public UUID getOutboxEventId() { return outboxEventId; }
}
