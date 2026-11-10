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
@Table(name = "stock_balances")
public class StockBalanceEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "stock_location_id", nullable = false, updatable = false)
    private UUID stockLocationId;

    @Column(name = "ingredient_id", nullable = false, updatable = false)
    private UUID ingredientId;

    @Column(name = "quantity_on_hand", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantityOnHand;

    @Column(name = "quantity_reserved", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantityReserved = BigDecimal.ZERO;

    @Column(name = "average_unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal averageUnitCost = BigDecimal.ZERO;

    @Column(name = "last_movement_at")
    private Instant lastMovementAt;

    @Column(name = "last_cost_updated_at")
    private Instant lastCostUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StockBalanceEntity() {
    }

    public StockBalanceEntity(
            UUID id,
            UUID tenantId,
            UUID branchId,
            UUID stockLocationId,
            UUID ingredientId,
            BigDecimal quantityOnHand
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.stockLocationId = stockLocationId;
        this.ingredientId = ingredientId;
        this.quantityOnHand = quantityOnHand;
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
    public UUID getStockLocationId() { return stockLocationId; }
    public UUID getIngredientId() { return ingredientId; }
    public BigDecimal getQuantityOnHand() { return quantityOnHand; }
    public BigDecimal getQuantityReserved() { return quantityReserved; }
    public BigDecimal getAverageUnitCost() { return averageUnitCost; }
    public Instant getLastMovementAt() { return lastMovementAt; }
    public Instant getLastCostUpdatedAt() { return lastCostUpdatedAt; }
    public long getVersion() { return version; }

    public void setQuantityOnHand(BigDecimal quantityOnHand) { this.quantityOnHand = quantityOnHand; }
    public void setQuantityReserved(BigDecimal quantityReserved) { this.quantityReserved = quantityReserved; }
    public void setAverageUnitCost(BigDecimal averageUnitCost) { this.averageUnitCost = averageUnitCost; }
    public void setLastMovementAt(Instant lastMovementAt) { this.lastMovementAt = lastMovementAt; }
    public void setLastCostUpdatedAt(Instant lastCostUpdatedAt) { this.lastCostUpdatedAt = lastCostUpdatedAt; }
    public void setVersion(long version) { this.version = version; }
}
