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
@Table(name = "product_variants")
public class ProductVariantEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "sku", nullable = false, length = 80)
    private String sku;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "price_override", nullable = false, precision = 14, scale = 4)
    private BigDecimal priceOverride;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ProductVariantEntity() {
    }

    public ProductVariantEntity(UUID id, UUID tenantId, UUID productId, String sku, String barcode, String name, BigDecimal priceOverride, int displayOrder, boolean active) {
        this.id = id;
        this.tenantId = tenantId;
        this.productId = productId;
        this.sku = sku;
        this.barcode = barcode;
        this.name = name;
        this.priceOverride = priceOverride;
        this.displayOrder = displayOrder;
        this.active = active;
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
    public UUID getProductId() { return productId; }
    public String getSku() { return sku; }
    public String getBarcode() { return barcode; }
    public String getName() { return name; }
    public BigDecimal getPriceOverride() { return priceOverride; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
    public void setName(String name) { this.name = name; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public void setPriceOverride(BigDecimal priceOverride) { this.priceOverride = priceOverride; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public void setActive(boolean active) { this.active = active; }
}
