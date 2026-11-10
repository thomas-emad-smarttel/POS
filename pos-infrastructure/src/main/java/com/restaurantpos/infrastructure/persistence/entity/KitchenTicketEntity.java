package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kitchen_tickets")
public class KitchenTicketEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_number_snapshot", nullable = false, updatable = false, length = 64)
    private String orderNumberSnapshot;

    @Column(name = "order_type_snapshot", nullable = false, updatable = false, length = 32)
    private String orderTypeSnapshot;

    @Column(name = "table_label_snapshot", updatable = false, length = 100)
    private String tableLabelSnapshot;

    @Column(name = "waiter_id", updatable = false)
    private UUID waiterId;

    @Column(name = "waiter_name_snapshot", updatable = false, length = 120)
    private String waiterNameSnapshot;

    @Column(name = "kitchen_station_id", nullable = false, updatable = false)
    private UUID kitchenStationId;

    @Column(name = "source_outbox_event_id", updatable = false)
    private UUID sourceOutboxEventId;

    @Column(name = "ticket_number", nullable = false, updatable = false, length = 64)
    private String ticketNumber;

    @Column(name = "ticket_kind", nullable = false, updatable = false, length = 32)
    private String ticketKind;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "priority_level", nullable = false, length = 16)
    private String priorityLevel;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "expected_prep_minutes", nullable = false)
    private int expectedPrepMinutes;

    @Column(name = "fired_at", nullable = false, updatable = false)
    private Instant firedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "bumped_at")
    private Instant bumpedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Column(name = "hold_reason", length = 300)
    private String holdReason;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected KitchenTicketEntity() {
    }

    public KitchenTicketEntity(
            UUID id, UUID tenantId, UUID branchId, UUID orderId, String orderNumberSnapshot,
            String orderTypeSnapshot, String tableLabelSnapshot, UUID waiterId, String waiterNameSnapshot,
            UUID kitchenStationId, UUID sourceOutboxEventId, String ticketNumber, String ticketKind,
            String status, String priorityLevel, int priority, int expectedPrepMinutes, Instant firedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.orderId = orderId;
        this.orderNumberSnapshot = orderNumberSnapshot;
        this.orderTypeSnapshot = orderTypeSnapshot;
        this.tableLabelSnapshot = tableLabelSnapshot;
        this.waiterId = waiterId;
        this.waiterNameSnapshot = waiterNameSnapshot;
        this.kitchenStationId = kitchenStationId;
        this.sourceOutboxEventId = sourceOutboxEventId;
        this.ticketNumber = ticketNumber;
        this.ticketKind = ticketKind;
        this.status = status;
        this.priorityLevel = priorityLevel;
        this.priority = priority;
        this.expectedPrepMinutes = expectedPrepMinutes;
        this.firedAt = firedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getOrderId() { return orderId; }
    public String getOrderNumberSnapshot() { return orderNumberSnapshot; }
    public String getOrderTypeSnapshot() { return orderTypeSnapshot; }
    public String getTableLabelSnapshot() { return tableLabelSnapshot; }
    public UUID getWaiterId() { return waiterId; }
    public String getWaiterNameSnapshot() { return waiterNameSnapshot; }
    public UUID getKitchenStationId() { return kitchenStationId; }
    public UUID getSourceOutboxEventId() { return sourceOutboxEventId; }
    public String getTicketNumber() { return ticketNumber; }
    public String getTicketKind() { return ticketKind; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(String priorityLevel) { this.priorityLevel = priorityLevel; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public int getExpectedPrepMinutes() { return expectedPrepMinutes; }
    public void setExpectedPrepMinutes(int expectedPrepMinutes) { this.expectedPrepMinutes = expectedPrepMinutes; }
    public Instant getFiredAt() { return firedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getReadyAt() { return readyAt; }
    public void setReadyAt(Instant readyAt) { this.readyAt = readyAt; }
    public Instant getBumpedAt() { return bumpedAt; }
    public void setBumpedAt(Instant bumpedAt) { this.bumpedAt = bumpedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public UUID getCancelledByUserId() { return cancelledByUserId; }
    public void setCancelledByUserId(UUID cancelledByUserId) { this.cancelledByUserId = cancelledByUserId; }
    public String getHoldReason() { return holdReason; }
    public void setHoldReason(String holdReason) { this.holdReason = holdReason; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public long getVersion() { return version; }
}
