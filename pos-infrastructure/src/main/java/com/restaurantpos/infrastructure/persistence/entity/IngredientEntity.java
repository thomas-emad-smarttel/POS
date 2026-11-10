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
@Table(name = "ingredients")
public class IngredientEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "default_location_id")
    private UUID defaultLocationId;

    @Column(name = "sku", nullable = false, length = 80)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "item_type", nullable = false, length = 32)
    private String itemType = "RAW_INGREDIENT";

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "minimum_stock_level", nullable = false, precision = 14, scale = 4)
    private BigDecimal minimumStockLevel = BigDecimal.ZERO;

    @Column(name = "reorder_level", nullable = false, precision = 14, scale = 4)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(name = "average_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal averageCost = BigDecimal.ZERO;

    @Column(name = "tracking_policy", nullable = false, length = 32)
    private String trackingPolicy = "STANDARD";

    @Column(name = "expiry_tracking", nullable = false)
    private boolean expiryTracking;

    @Column(name = "batch_tracking", nullable = false)
    private boolean batchTracking;

    @Column(name = "negative_stock_policy", nullable = false, length = 32)
    private String negativeStockPolicy = "INHERIT";

    @Column(name = "costing_method", nullable = false, length = 32)
    private String costingMethod = "WEIGHTED_AVERAGE";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected IngredientEntity() {
    }

    public IngredientEntity(UUID id, UUID tenantId, String sku, String name, UUID unitId) {
        this.id = id;
        this.tenantId = tenantId;
        this.sku = sku;
        this.name = name;
        this.unitId = unitId;
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
    public UUID getCategoryId() { return categoryId; }
    public UUID getDefaultLocationId() { return defaultLocationId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getItemType() { return itemType; }
    public String getBarcode() { return barcode; }
    public UUID getUnitId() { return unitId; }
    public BigDecimal getMinimumStockLevel() { return minimumStockLevel; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public BigDecimal getAverageCost() { return averageCost; }
    public String getTrackingPolicy() { return trackingPolicy; }
    public boolean isExpiryTracking() { return expiryTracking; }
    public boolean isBatchTracking() { return batchTracking; }
    public String getNegativeStockPolicy() { return negativeStockPolicy; }
    public String getCostingMethod() { return costingMethod; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }

    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public void setDefaultLocationId(UUID defaultLocationId) { this.defaultLocationId = defaultLocationId; }
    public void setSku(String sku) { this.sku = sku; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public void setUnitId(UUID unitId) { this.unitId = unitId; }
    public void setMinimumStockLevel(BigDecimal minimumStockLevel) { this.minimumStockLevel = minimumStockLevel; }
    public void setReorderLevel(BigDecimal reorderLevel) { this.reorderLevel = reorderLevel; }
    public void setAverageCost(BigDecimal averageCost) { this.averageCost = averageCost; }
    public void setTrackingPolicy(String trackingPolicy) { this.trackingPolicy = trackingPolicy; }
    public void setExpiryTracking(boolean expiryTracking) { this.expiryTracking = expiryTracking; }
    public void setBatchTracking(boolean batchTracking) { this.batchTracking = batchTracking; }
    public void setNegativeStockPolicy(String negativeStockPolicy) { this.negativeStockPolicy = negativeStockPolicy; }
    public void setCostingMethod(String costingMethod) { this.costingMethod = costingMethod; }
    public void setActive(boolean active) { this.active = active; }
}
