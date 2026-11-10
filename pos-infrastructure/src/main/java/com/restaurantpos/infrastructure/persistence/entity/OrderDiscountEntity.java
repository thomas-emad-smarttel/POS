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
@Table(name = "order_discounts")
public class OrderDiscountEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_item_id", updatable = false)
    private UUID orderItemId;

    @Column(name = "discount_code", updatable = false, length = 64)
    private String discountCode;

    @Column(name = "discount_name", nullable = false, updatable = false, length = 150)
    private String discountName;

    @Column(name = "discount_type", nullable = false, updatable = false, length = 32)
    private String discountType;

    @Column(name = "discount_value", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "applied_amount", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal appliedAmount;

    @Column(name = "applied_by_user_id", nullable = false, updatable = false)
    private UUID appliedByUserId;

    @Column(name = "approved_by_user_id", updatable = false)
    private UUID approvedByUserId;

    @Column(name = "reason", updatable = false, length = 300)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderDiscountEntity() {
    }

    public OrderDiscountEntity(
            UUID id, UUID tenantId, UUID branchId, UUID orderId, UUID orderItemId,
            String discountCode, String discountName, String discountType,
            BigDecimal discountValue, BigDecimal appliedAmount,
            UUID appliedByUserId, UUID approvedByUserId, String reason
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.discountCode = discountCode;
        this.discountName = discountName;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.appliedAmount = appliedAmount;
        this.appliedByUserId = appliedByUserId;
        this.approvedByUserId = approvedByUserId;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getOrderItemId() { return orderItemId; }
    public String getDiscountCode() { return discountCode; }
    public String getDiscountName() { return discountName; }
    public String getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public BigDecimal getAppliedAmount() { return appliedAmount; }
    public UUID getAppliedByUserId() { return appliedByUserId; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public String getReason() { return reason; }
}
