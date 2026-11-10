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
@Table(name = "order_taxes")
public class OrderTaxEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "tax_id", updatable = false)
    private UUID taxId;

    @Column(name = "tax_name_snapshot", nullable = false, updatable = false, length = 120)
    private String taxNameSnapshot;

    @Column(name = "rate_percent_snapshot", nullable = false, updatable = false, precision = 7, scale = 4)
    private BigDecimal ratePercentSnapshot;

    @Column(name = "inclusive", nullable = false, updatable = false)
    private boolean inclusive;

    @Column(name = "taxable_amount", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal taxableAmount;

    @Column(name = "tax_amount", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderTaxEntity() {
    }

    public OrderTaxEntity(
            UUID id, UUID tenantId, UUID branchId, UUID orderId, UUID taxId,
            String taxNameSnapshot, BigDecimal ratePercentSnapshot, boolean inclusive,
            BigDecimal taxableAmount, BigDecimal taxAmount
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.orderId = orderId;
        this.taxId = taxId;
        this.taxNameSnapshot = taxNameSnapshot;
        this.ratePercentSnapshot = ratePercentSnapshot;
        this.inclusive = inclusive;
        this.taxableAmount = taxableAmount;
        this.taxAmount = taxAmount;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTaxId() { return taxId; }
    public String getTaxNameSnapshot() { return taxNameSnapshot; }
    public BigDecimal getRatePercentSnapshot() { return ratePercentSnapshot; }
    public boolean isInclusive() { return inclusive; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
}
