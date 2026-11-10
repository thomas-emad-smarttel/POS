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
@Table(name = "branch_product_overrides")
public class BranchProductOverrideEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "price_override", precision = 14, scale = 4)
    private BigDecimal priceOverride;

    @Column(name = "tax_override_id")
    private UUID taxOverrideId;

    @Column(name = "available", nullable = false)
    private boolean available = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected BranchProductOverrideEntity() {
    }

    public BranchProductOverrideEntity(UUID id, UUID tenantId, UUID branchId, UUID productId, BigDecimal priceOverride, boolean available) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.productId = productId;
        this.priceOverride = priceOverride;
        this.available = available;
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
    public UUID getProductId() { return productId; }
    public BigDecimal getPriceOverride() { return priceOverride; }
    public UUID getTaxOverrideId() { return taxOverrideId; }
    public boolean isAvailable() { return available; }
    public long getVersion() { return version; }

    public void setPriceOverride(BigDecimal priceOverride) { this.priceOverride = priceOverride; }
    public void setTaxOverrideId(UUID taxOverrideId) { this.taxOverrideId = taxOverrideId; }
    public void setAvailable(boolean available) { this.available = available; }
}
