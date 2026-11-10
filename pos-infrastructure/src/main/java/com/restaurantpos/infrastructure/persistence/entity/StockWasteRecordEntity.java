package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_waste_records")
public class StockWasteRecordEntity {

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

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Column(name = "original_quantity", nullable = false, precision = 14, scale = 6)
    private BigDecimal originalQuantity;

    @Column(name = "base_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal baseQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "total_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal totalCost;

    @Column(name = "reason_code", nullable = false, length = 48)
    private String reasonCode;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "attachment_ref", length = 300)
    private String attachmentRef;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "supervisor_approval_id")
    private UUID supervisorApprovalId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "movement_id")
    private UUID movementId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected StockWasteRecordEntity() {
    }

    public StockWasteRecordEntity(
            UUID id, UUID tenantId, UUID branchId, UUID stockLocationId, UUID ingredientId, UUID unitId,
            BigDecimal originalQuantity, BigDecimal baseQuantity, BigDecimal unitCost, BigDecimal totalCost,
            String reasonCode, String notes, String attachmentRef, UUID actorUserId, UUID supervisorApprovalId,
            String idempotencyKey, UUID movementId, Instant occurredAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.stockLocationId = stockLocationId;
        this.ingredientId = ingredientId;
        this.unitId = unitId;
        this.originalQuantity = originalQuantity;
        this.baseQuantity = baseQuantity;
        this.unitCost = unitCost;
        this.totalCost = totalCost;
        this.reasonCode = reasonCode;
        this.notes = notes;
        this.attachmentRef = attachmentRef;
        this.actorUserId = actorUserId;
        this.supervisorApprovalId = supervisorApprovalId;
        this.idempotencyKey = idempotencyKey;
        this.movementId = movementId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getStockLocationId() { return stockLocationId; }
    public UUID getIngredientId() { return ingredientId; }
    public UUID getUnitId() { return unitId; }
    public BigDecimal getOriginalQuantity() { return originalQuantity; }
    public BigDecimal getBaseQuantity() { return baseQuantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getTotalCost() { return totalCost; }
    public String getReasonCode() { return reasonCode; }
    public String getNotes() { return notes; }
    public String getAttachmentRef() { return attachmentRef; }
    public UUID getActorUserId() { return actorUserId; }
    public UUID getSupervisorApprovalId() { return supervisorApprovalId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getMovementId() { return movementId; }
    public Instant getOccurredAt() { return occurredAt; }
}
