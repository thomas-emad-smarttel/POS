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
@Table(name = "order_items")
public class OrderItemEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "product_id", updatable = false)
    private UUID productId;

    @Column(name = "product_variant_id", updatable = false)
    private UUID productVariantId;

    @Column(name = "tax_id", updatable = false)
    private UUID taxId;

    @Column(name = "tax_name_snapshot", updatable = false, length = 120)
    private String taxNameSnapshot;

    @Column(name = "tax_inclusive", nullable = false, updatable = false)
    private boolean taxInclusive;

    @Column(name = "sku_snapshot", nullable = false, updatable = false, length = 80)
    private String skuSnapshot;

    @Column(name = "product_name_snapshot", nullable = false, updatable = false, length = 200)
    private String productNameSnapshot;

    @Column(name = "variant_name_snapshot", updatable = false, length = 150)
    private String variantNameSnapshot;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price_snapshot", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "tax_rate_snapshot", nullable = false, updatable = false, precision = 7, scale = 4)
    private BigDecimal taxRateSnapshot;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 4)
    private BigDecimal lineTotal;

    @Column(name = "kitchen_notes", length = 500)
    private String kitchenNotes;

    @Column(name = "sent_to_kitchen", nullable = false)
    private boolean sentToKitchen;

    @Column(name = "voided", nullable = false)
    private boolean voided;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected OrderItemEntity() {
    }

    public OrderItemEntity(
            UUID id,
            UUID tenantId,
            UUID branchId,
            UUID orderId,
            UUID productId,
            String skuSnapshot,
            String productNameSnapshot,
            String variantNameSnapshot,
            BigDecimal quantity,
            BigDecimal unitPriceSnapshot,
            BigDecimal taxRateSnapshot,
            BigDecimal taxAmount,
            BigDecimal discountAmount,
            BigDecimal lineTotal
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.orderId = orderId;
        this.productId = productId;
        this.skuSnapshot = skuSnapshot;
        this.productNameSnapshot = productNameSnapshot;
        this.variantNameSnapshot = variantNameSnapshot;
        this.quantity = quantity;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.taxRateSnapshot = taxRateSnapshot;
        this.taxAmount = taxAmount;
        this.discountAmount = discountAmount;
        this.lineTotal = lineTotal;
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
    public UUID getOrderId() { return orderId; }
    public UUID getProductId() { return productId; }
    public UUID getProductVariantId() { return productVariantId; }
    public UUID getTaxId() { return taxId; }
    public String getTaxNameSnapshot() { return taxNameSnapshot; }
    public boolean isTaxInclusive() { return taxInclusive; }
    public String getSkuSnapshot() { return skuSnapshot; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public String getVariantNameSnapshot() { return variantNameSnapshot; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }
    public BigDecimal getTaxRateSnapshot() { return taxRateSnapshot; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public String getKitchenNotes() { return kitchenNotes; }
    public boolean isSentToKitchen() { return sentToKitchen; }
    public boolean isVoided() { return voided; }
    public long getVersion() { return version; }

    public void setProductVariantId(UUID productVariantId) { this.productVariantId = productVariantId; }
    public void setTaxId(UUID taxId) { this.taxId = taxId; }
    public void setTaxNameSnapshot(String taxNameSnapshot) { this.taxNameSnapshot = taxNameSnapshot; }
    public void setTaxInclusive(boolean taxInclusive) { this.taxInclusive = taxInclusive; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
    public void setKitchenNotes(String kitchenNotes) { this.kitchenNotes = kitchenNotes; }
    public void setSentToKitchen(boolean sentToKitchen) { this.sentToKitchen = sentToKitchen; }
    public void setVoided(boolean voided) { this.voided = voided; }
}
