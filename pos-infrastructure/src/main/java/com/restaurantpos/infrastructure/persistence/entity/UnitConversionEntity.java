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
@Table(name = "unit_conversions")
public class UnitConversionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;

    @Column(name = "from_unit_id", nullable = false)
    private UUID fromUnitId;

    @Column(name = "to_unit_id", nullable = false)
    private UUID toUnitId;

    @Column(name = "conversion_factor", nullable = false, precision = 18, scale = 6)
    private BigDecimal conversionFactor;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UnitConversionEntity() {
    }

    public UnitConversionEntity(UUID id, UUID tenantId, UUID inventoryItemId, UUID fromUnitId, UUID toUnitId, BigDecimal conversionFactor, boolean active) {
        this.id = id;
        this.tenantId = tenantId;
        this.inventoryItemId = inventoryItemId;
        this.fromUnitId = fromUnitId;
        this.toUnitId = toUnitId;
        this.conversionFactor = conversionFactor;
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
    public UUID getInventoryItemId() { return inventoryItemId; }
    public UUID getFromUnitId() { return fromUnitId; }
    public UUID getToUnitId() { return toUnitId; }
    public BigDecimal getConversionFactor() { return conversionFactor; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }

    public void setConversionFactor(BigDecimal conversionFactor) { this.conversionFactor = conversionFactor; }
    public void setActive(boolean active) { this.active = active; }
}
