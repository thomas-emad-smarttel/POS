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
@Table(name = "stock_reservations")
public class StockReservationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "recipe_snapshot_json")
    private String recipeSnapshotJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StockReservationEntity() {
    }

    public StockReservationEntity(UUID id, UUID tenantId, UUID branchId, UUID orderId) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.orderId = orderId;
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
    public UUID getSourceEventId() { return sourceEventId; }
    public String getStatus() { return status; }
    public String getRecipeSnapshotJson() { return recipeSnapshotJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void setSourceEventId(UUID sourceEventId) { this.sourceEventId = sourceEventId; }
    public void setStatus(String status) { this.status = status; }
    public void setRecipeSnapshotJson(String recipeSnapshotJson) { this.recipeSnapshotJson = recipeSnapshotJson; }
}
