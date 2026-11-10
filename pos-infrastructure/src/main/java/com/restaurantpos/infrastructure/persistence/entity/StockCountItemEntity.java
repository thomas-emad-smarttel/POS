package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_count_items")
public class StockCountItemEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "stock_count_id", nullable = false, updatable = false)
    private UUID stockCountId;

    @Column(name = "ingredient_id", nullable = false, updatable = false)
    private UUID ingredientId;

    @Column(name = "system_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal systemQuantity = BigDecimal.ZERO;

    @Column(name = "counted_quantity", precision = 14, scale = 4)
    private BigDecimal countedQuantity;

    @Column(name = "variance_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal varianceQuantity = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "variance_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal varianceCost = BigDecimal.ZERO;

    @Column(name = "recount_required", nullable = false)
    private boolean recountRequired;

    @Column(name = "original_counted_quantity", precision = 14, scale = 4)
    private BigDecimal originalCountedQuantity;

    @Column(name = "notes", length = 300)
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StockCountItemEntity() {
    }

    public StockCountItemEntity(UUID id, UUID tenantId, UUID stockCountId, UUID ingredientId) {
        this.id = id;
        this.tenantId = tenantId;
        this.stockCountId = stockCountId;
        this.ingredientId = ingredientId;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStockCountId() { return stockCountId; }
    public UUID getIngredientId() { return ingredientId; }
    public BigDecimal getSystemQuantity() { return systemQuantity; }
    public BigDecimal getCountedQuantity() { return countedQuantity; }
    public BigDecimal getVarianceQuantity() { return varianceQuantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getVarianceCost() { return varianceCost; }
    public boolean isRecountRequired() { return recountRequired; }
    public BigDecimal getOriginalCountedQuantity() { return originalCountedQuantity; }
    public String getNotes() { return notes; }
    public long getVersion() { return version; }

    public void setSystemQuantity(BigDecimal systemQuantity) { this.systemQuantity = systemQuantity; }
    public void setCountedQuantity(BigDecimal countedQuantity) { this.countedQuantity = countedQuantity; }
    public void setVarianceQuantity(BigDecimal varianceQuantity) { this.varianceQuantity = varianceQuantity; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public void setVarianceCost(BigDecimal varianceCost) { this.varianceCost = varianceCost; }
    public void setRecountRequired(boolean recountRequired) { this.recountRequired = recountRequired; }
    public void setOriginalCountedQuantity(BigDecimal originalCountedQuantity) { this.originalCountedQuantity = originalCountedQuantity; }
    public void setNotes(String notes) { this.notes = notes; }
}
