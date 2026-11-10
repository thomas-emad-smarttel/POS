package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "stock_movements")
public class StockMovementEntity {

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

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "movement_type", nullable = false, updatable = false, length = 32)
    private String movementType;

    @Column(name = "direction", nullable = false, updatable = false, length = 16)
    private String direction = "IN";

    @Column(name = "quantity_delta", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal quantityDelta;

    @Column(name = "unit_id", updatable = false)
    private UUID unitId;

    @Column(name = "original_quantity", updatable = false, precision = 14, scale = 6)
    private BigDecimal originalQuantity;

    @Column(name = "balance_before", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal balanceBefore = BigDecimal.ZERO;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal balanceAfter = BigDecimal.ZERO;

    @Column(name = "reserved_before", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal reservedBefore = BigDecimal.ZERO;

    @Column(name = "reserved_after", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal reservedAfter = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "total_cost", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "reference_type", updatable = false, length = 64)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    @Column(name = "reason", updatable = false, length = 300)
    private String reason;

    @Column(name = "batch_number", updatable = false, length = 80)
    private String batchNumber;

    @Column(name = "expiry_date", updatable = false, length = 32)
    private String expiryDate;

    @Column(name = "approval_id", updatable = false)
    private UUID approvalId;

    @Column(name = "outbox_event_id", updatable = false)
    private UUID outboxEventId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected StockMovementEntity() {
    }

    public StockMovementEntity(
            UUID id, UUID tenantId, UUID branchId, UUID stockLocationId, UUID ingredientId,
            String movementType, BigDecimal quantityDelta, BigDecimal unitCost,
            String idempotencyKey, Instant occurredAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.stockLocationId = stockLocationId;
        this.ingredientId = ingredientId;
        this.movementType = movementType;
        this.direction = quantityDelta.compareTo(BigDecimal.ZERO) >= 0 ? "IN" : "OUT";
        this.quantityDelta = quantityDelta;
        this.unitCost = unitCost;
        this.idempotencyKey = idempotencyKey;
        this.occurredAt = occurredAt;
    }

    public StockMovementEntity(
            UUID id, UUID tenantId, UUID branchId, UUID stockLocationId, UUID ingredientId,
            UUID actorUserId, String movementType, String direction, BigDecimal quantityDelta,
            UUID unitId, BigDecimal originalQuantity, BigDecimal balanceBefore, BigDecimal balanceAfter,
            BigDecimal reservedBefore, BigDecimal reservedAfter, BigDecimal unitCost, BigDecimal totalCost,
            String referenceType, UUID referenceId, String reason, String batchNumber, String expiryDate,
            UUID approvalId, UUID outboxEventId, String idempotencyKey, Instant occurredAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.stockLocationId = stockLocationId;
        this.ingredientId = ingredientId;
        this.actorUserId = actorUserId;
        this.movementType = movementType;
        this.direction = direction;
        this.quantityDelta = quantityDelta;
        this.unitId = unitId;
        this.originalQuantity = originalQuantity;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.reservedBefore = reservedBefore;
        this.reservedAfter = reservedAfter;
        this.unitCost = unitCost;
        this.totalCost = totalCost;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.reason = reason;
        this.batchNumber = batchNumber;
        this.expiryDate = expiryDate;
        this.approvalId = approvalId;
        this.outboxEventId = outboxEventId;
        this.idempotencyKey = idempotencyKey;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getStockLocationId() { return stockLocationId; }
    public UUID getIngredientId() { return ingredientId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getMovementType() { return movementType; }
    public String getDirection() { return direction; }
    public BigDecimal getQuantityDelta() { return quantityDelta; }
    public UUID getUnitId() { return unitId; }
    public BigDecimal getOriginalQuantity() { return originalQuantity; }
    public BigDecimal getBalanceBefore() { return balanceBefore; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public BigDecimal getReservedBefore() { return reservedBefore; }
    public BigDecimal getReservedAfter() { return reservedAfter; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getTotalCost() { return totalCost; }
    public String getReferenceType() { return referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public String getReason() { return reason; }
    public String getBatchNumber() { return batchNumber; }
    public String getExpiryDate() { return expiryDate; }
    public UUID getApprovalId() { return approvalId; }
    public UUID getOutboxEventId() { return outboxEventId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getOccurredAt() { return occurredAt; }
}
