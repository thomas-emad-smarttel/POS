package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kitchen_ticket_items")
public class KitchenTicketItemEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "kitchen_ticket_id", nullable = false, updatable = false)
    private UUID kitchenTicketId;

    @Column(name = "order_item_id", nullable = false, updatable = false)
    private UUID orderItemId;

    @Column(name = "item_name_snapshot", nullable = false, updatable = false, length = 200)
    private String itemNameSnapshot;

    @Column(name = "variant_name_snapshot", updatable = false, length = 150)
    private String variantNameSnapshot;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(name = "modifiers_snapshot_json", updatable = false, columnDefinition = "TEXT")
    private String modifiersSnapshotJson;

    @Column(name = "prep_instructions", updatable = false, length = 500)
    private String prepInstructions;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "expected_prep_minutes", nullable = false)
    private int expectedPrepMinutes;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "served_at")
    private Instant servedAt;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected KitchenTicketItemEntity() {
    }

    public KitchenTicketItemEntity(
            UUID id, UUID tenantId, UUID branchId, UUID kitchenTicketId, UUID orderItemId,
            String itemNameSnapshot, String variantNameSnapshot, BigDecimal quantity,
            String modifiersSnapshotJson, String prepInstructions, String notes,
            int expectedPrepMinutes, String status
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.kitchenTicketId = kitchenTicketId;
        this.orderItemId = orderItemId;
        this.itemNameSnapshot = itemNameSnapshot;
        this.variantNameSnapshot = variantNameSnapshot;
        this.quantity = quantity;
        this.modifiersSnapshotJson = modifiersSnapshotJson;
        this.prepInstructions = prepInstructions;
        this.notes = notes;
        this.expectedPrepMinutes = expectedPrepMinutes;
        this.status = status;
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
    public UUID getKitchenTicketId() { return kitchenTicketId; }
    public UUID getOrderItemId() { return orderItemId; }
    public String getItemNameSnapshot() { return itemNameSnapshot; }
    public String getVariantNameSnapshot() { return variantNameSnapshot; }
    public BigDecimal getQuantity() { return quantity; }
    public String getModifiersSnapshotJson() { return modifiersSnapshotJson; }
    public String getPrepInstructions() { return prepInstructions; }
    public String getNotes() { return notes; }
    public int getExpectedPrepMinutes() { return expectedPrepMinutes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getReadyAt() { return readyAt; }
    public void setReadyAt(Instant readyAt) { this.readyAt = readyAt; }
    public Instant getServedAt() { return servedAt; }
    public void setServedAt(Instant servedAt) { this.servedAt = servedAt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public long getVersion() { return version; }
}
