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
@Table(name = "kitchen_ticket_events")
public class KitchenTicketEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "kitchen_ticket_id", nullable = false, updatable = false)
    private UUID kitchenTicketId;

    @Column(name = "kitchen_ticket_item_id", updatable = false)
    private UUID kitchenTicketItemId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 32)
    private String eventType;

    @Column(name = "previous_status", updatable = false, length = 32)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, updatable = false, length = 32)
    private String newStatus;

    @Column(name = "reason", updatable = false, length = 300)
    private String reason;

    @Column(name = "details_json", updatable = false, columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected KitchenTicketEventEntity() {
    }

    public KitchenTicketEventEntity(
            UUID id, UUID tenantId, UUID branchId, UUID kitchenTicketId, UUID kitchenTicketItemId,
            UUID actorUserId, String eventType, String previousStatus, String newStatus,
            String reason, String detailsJson, Instant occurredAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.kitchenTicketId = kitchenTicketId;
        this.kitchenTicketItemId = kitchenTicketItemId;
        this.actorUserId = actorUserId;
        this.eventType = eventType;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.detailsJson = detailsJson;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getKitchenTicketId() { return kitchenTicketId; }
    public UUID getKitchenTicketItemId() { return kitchenTicketItemId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getEventType() { return eventType; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public String getDetailsJson() { return detailsJson; }
    public Instant getOccurredAt() { return occurredAt; }
}
