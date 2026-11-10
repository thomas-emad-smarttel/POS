package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "stock_transfer_items")
public class StockTransferItemEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "stock_transfer_id", nullable = false, updatable = false)
    private UUID stockTransferId;

    @Column(name = "ingredient_id", nullable = false, updatable = false)
    private UUID ingredientId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Column(name = "requested_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal requestedQuantity;

    @Column(name = "dispatched_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal dispatchedQuantity = BigDecimal.ZERO;

    @Column(name = "received_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @Column(name = "damaged_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal damagedQuantity = BigDecimal.ZERO;

    @Column(name = "missing_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal missingQuantity = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "notes", length = 300)
    private String notes;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StockTransferItemEntity() {
    }

    public StockTransferItemEntity(UUID id, UUID tenantId, UUID stockTransferId, UUID ingredientId, UUID unitId) {
        this.id = id;
        this.tenantId = tenantId;
        this.stockTransferId = stockTransferId;
        this.ingredientId = ingredientId;
        this.unitId = unitId;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStockTransferId() { return stockTransferId; }
    public UUID getIngredientId() { return ingredientId; }
    public UUID getUnitId() { return unitId; }
    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public BigDecimal getDispatchedQuantity() { return dispatchedQuantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public BigDecimal getDamagedQuantity() { return damagedQuantity; }
    public BigDecimal getMissingQuantity() { return missingQuantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getNotes() { return notes; }
    public long getVersion() { return version; }

    public void setRequestedQuantity(BigDecimal requestedQuantity) { this.requestedQuantity = requestedQuantity; }
    public void setDispatchedQuantity(BigDecimal dispatchedQuantity) { this.dispatchedQuantity = dispatchedQuantity; }
    public void setReceivedQuantity(BigDecimal receivedQuantity) { this.receivedQuantity = receivedQuantity; }
    public void setDamagedQuantity(BigDecimal damagedQuantity) { this.damagedQuantity = damagedQuantity; }
    public void setMissingQuantity(BigDecimal missingQuantity) { this.missingQuantity = missingQuantity; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public void setNotes(String notes) { this.notes = notes; }
}
