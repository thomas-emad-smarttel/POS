package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kitchen_unrouted_items")
public class KitchenUnroutedItemEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "outbox_event_id", nullable = false, updatable = false)
    private UUID outboxEventId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false, updatable = false, length = 64)
    private String orderNumber;

    @Column(name = "order_item_id", nullable = false, updatable = false)
    private UUID orderItemId;

    @Column(name = "product_id", updatable = false)
    private UUID productId;

    @Column(name = "product_name_snapshot", nullable = false, updatable = false, length = 200)
    private String productNameSnapshot;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(name = "modifiers_summary", updatable = false, length = 500)
    private String modifiersSummary;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "resolved_station_id")
    private UUID resolvedStationId;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected KitchenUnroutedItemEntity() {
    }

    public KitchenUnroutedItemEntity(
            UUID id, UUID tenantId, UUID branchId, UUID outboxEventId, UUID orderId, String orderNumber,
            UUID orderItemId, UUID productId, String productNameSnapshot, BigDecimal quantity,
            String modifiersSummary, String status, Instant createdAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.outboxEventId = outboxEventId;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot;
        this.quantity = quantity;
        this.modifiersSummary = modifiersSummary;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public UUID getOutboxEventId() { return outboxEventId; }
    public UUID getOrderId() { return orderId; }
    public String getOrderNumber() { return orderNumber; }
    public UUID getOrderItemId() { return orderItemId; }
    public UUID getProductId() { return productId; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public BigDecimal getQuantity() { return quantity; }
    public String getModifiersSummary() { return modifiersSummary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getResolvedStationId() { return resolvedStationId; }
    public void setResolvedStationId(UUID resolvedStationId) { this.resolvedStationId = resolvedStationId; }
    public UUID getResolvedByUserId() { return resolvedByUserId; }
    public void setResolvedByUserId(UUID resolvedByUserId) { this.resolvedByUserId = resolvedByUserId; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
