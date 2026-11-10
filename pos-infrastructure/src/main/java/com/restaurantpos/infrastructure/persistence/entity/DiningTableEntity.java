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
@Table(name = "dining_tables")
public class DiningTableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "dining_area_id", nullable = false)
    private UUID diningAreaId;

    @Column(name = "table_code", nullable = false, length = 32)
    private String tableCode;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "active_order_id")
    private UUID activeOrderId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DiningTableEntity() {
    }

    public DiningTableEntity(UUID id, UUID tenantId, UUID branchId, UUID diningAreaId, String tableCode, String displayName, int capacity, int sortOrder, String status, UUID activeOrderId, boolean active) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.diningAreaId = diningAreaId;
        this.tableCode = tableCode;
        this.displayName = displayName;
        this.capacity = capacity;
        this.sortOrder = sortOrder;
        this.status = status;
        this.activeOrderId = activeOrderId;
        this.active = active;
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
    public UUID getDiningAreaId() { return diningAreaId; }
    public String getTableCode() { return tableCode; }
    public String getDisplayName() { return displayName; }
    public int getCapacity() { return capacity; }
    public int getSortOrder() { return sortOrder; }
    public String getStatus() { return status; }
    public UUID getActiveOrderId() { return activeOrderId; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }

    public void setDiningAreaId(UUID diningAreaId) { this.diningAreaId = diningAreaId; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setStatus(String status) { this.status = status; }
    public void setActiveOrderId(UUID activeOrderId) { this.activeOrderId = activeOrderId; }
    public void setActive(boolean active) { this.active = active; }
}
