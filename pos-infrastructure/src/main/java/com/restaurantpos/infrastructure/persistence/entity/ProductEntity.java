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
@Table(name = "products")
public class ProductEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "tax_id")
    private UUID taxId;

    @Column(name = "sku", nullable = false, length = 80)
    private String sku;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "base_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal basePrice;

    @Column(name = "cost_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "pricing_mode", nullable = false, length = 32)
    private String pricingMode = "FIXED";

    @Column(name = "product_type", nullable = false, length = 32)
    private String productType = "STANDARD";

    @Column(name = "image_ref", length = 500)
    private String imageRef;

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

    protected ProductEntity() {
    }

    public ProductEntity(UUID id, UUID tenantId, UUID categoryId, String sku, String name, BigDecimal basePrice) {
        this.id = id;
        this.tenantId = tenantId;
        this.categoryId = categoryId;
        this.sku = sku;
        this.name = name;
        this.basePrice = basePrice;
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
    public UUID getCategoryId() { return categoryId; }
    public UUID getTaxId() { return taxId; }
    public String getSku() { return sku; }
    public String getBarcode() { return barcode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getBasePrice() { return basePrice; }
    public String getPricingMode() { return pricingMode; }
    public String getProductType() { return productType; }
    public String getImageRef() { return imageRef; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }

    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public void setTaxId(UUID taxId) { this.taxId = taxId; }
    public void setSku(String sku) { this.sku = sku; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public void setPricingMode(String pricingMode) { this.pricingMode = pricingMode; }
    public void setProductType(String productType) { this.productType = productType; }
    public void setImageRef(String imageRef) { this.imageRef = imageRef; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public void setActive(boolean active) { this.active = active; }
}
