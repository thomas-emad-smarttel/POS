package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_item_modifiers")
public class OrderItemModifierEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "order_item_id", nullable = false, updatable = false)
    private UUID orderItemId;

    @Column(name = "modifier_id", updatable = false)
    private UUID modifierId;

    @Column(name = "modifier_name_snapshot", nullable = false, updatable = false, length = 150)
    private String modifierNameSnapshot;

    @Column(name = "unit_price_snapshot", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderItemModifierEntity() {
    }

    public OrderItemModifierEntity(
            UUID id, UUID tenantId, UUID branchId, UUID orderItemId, UUID modifierId,
            String modifierNameSnapshot, BigDecimal unitPriceSnapshot, int quantity
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.orderItemId = orderItemId;
        this.modifierId = modifierId;
        this.modifierNameSnapshot = modifierNameSnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.quantity = quantity;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrderItemId() { return orderItemId; }
    public UUID getModifierId() { return modifierId; }
    public String getModifierNameSnapshot() { return modifierNameSnapshot; }
    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }
    public int getQuantity() { return quantity; }
}
