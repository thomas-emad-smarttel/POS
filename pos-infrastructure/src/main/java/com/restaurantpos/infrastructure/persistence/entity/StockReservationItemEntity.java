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
@Table(name = "stock_reservation_items")
public class StockReservationItemEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "order_item_id", nullable = false, updatable = false)
    private UUID orderItemId;

    @Column(name = "kitchen_ticket_item_id")
    private UUID kitchenTicketItemId;

    @Column(name = "ingredient_id", nullable = false, updatable = false)
    private UUID ingredientId;

    @Column(name = "stock_location_id", nullable = false)
    private UUID stockLocationId;

    @Column(name = "recipe_version_id")
    private UUID recipeVersionId;

    @Column(name = "reserved_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "consumed_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal consumedQuantity = BigDecimal.ZERO;

    @Column(name = "released_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal releasedQuantity = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StockReservationItemEntity() {
    }

    public StockReservationItemEntity(UUID id, UUID tenantId, UUID branchId, UUID reservationId, UUID orderItemId, UUID ingredientId, String idempotencyKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.reservationId = reservationId;
        this.orderItemId = orderItemId;
        this.ingredientId = ingredientId;
        this.idempotencyKey = idempotencyKey;
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
    public UUID getReservationId() { return reservationId; }
    public UUID getOrderItemId() { return orderItemId; }
    public UUID getKitchenTicketItemId() { return kitchenTicketItemId; }
    public UUID getIngredientId() { return ingredientId; }
    public UUID getStockLocationId() { return stockLocationId; }
    public UUID getRecipeVersionId() { return recipeVersionId; }
    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public BigDecimal getConsumedQuantity() { return consumedQuantity; }
    public BigDecimal getReleasedQuantity() { return releasedQuantity; }
    public String getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getVersion() { return version; }

    public void setKitchenTicketItemId(UUID kitchenTicketItemId) { this.kitchenTicketItemId = kitchenTicketItemId; }
    public void setStockLocationId(UUID stockLocationId) { this.stockLocationId = stockLocationId; }
    public void setRecipeVersionId(UUID recipeVersionId) { this.recipeVersionId = recipeVersionId; }
    public void setReservedQuantity(BigDecimal reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public void setConsumedQuantity(BigDecimal consumedQuantity) { this.consumedQuantity = consumedQuantity; }
    public void setReleasedQuantity(BigDecimal releasedQuantity) { this.releasedQuantity = releasedQuantity; }
    public void setStatus(String status) { this.status = status; }
}
