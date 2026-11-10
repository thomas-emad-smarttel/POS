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
@Table(name = "inventory_alerts")
public class InventoryAlertEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "inventory_item_id", nullable = false, updatable = false)
    private UUID inventoryItemId;

    @Column(name = "stock_location_id")
    private UUID stockLocationId;

    @Column(name = "alert_type", nullable = false, length = 32)
    private String alertType;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "message", nullable = false, length = 400)
    private String message;

    @Column(name = "current_value", nullable = false, precision = 14, scale = 4)
    private BigDecimal currentValue;

    @Column(name = "threshold_value", nullable = false, precision = 14, scale = 4)
    private BigDecimal thresholdValue;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "dedup_key", nullable = false, length = 160)
    private String dedupKey;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected InventoryAlertEntity() {
    }

    public InventoryAlertEntity(UUID id, UUID tenantId, UUID branchId, UUID inventoryItemId, String dedupKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.inventoryItemId = inventoryItemId;
        this.dedupKey = dedupKey;
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
    public UUID getInventoryItemId() { return inventoryItemId; }
    public UUID getStockLocationId() { return stockLocationId; }
    public String getAlertType() { return alertType; }
    public String getSeverity() { return severity; }
    public String getMessage() { return message; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public String getStatus() { return status; }
    public String getDedupKey() { return dedupKey; }
    public UUID getResolvedByUserId() { return resolvedByUserId; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }

    public void setStockLocationId(UUID stockLocationId) { this.stockLocationId = stockLocationId; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public void setSeverity(String severity) { this.severity = severity; }
    public void setMessage(String message) { this.message = message; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }
    public void setThresholdValue(BigDecimal thresholdValue) { this.thresholdValue = thresholdValue; }
    public void setStatus(String status) { this.status = status; }
    public void setResolvedByUserId(UUID resolvedByUserId) { this.resolvedByUserId = resolvedByUserId; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
