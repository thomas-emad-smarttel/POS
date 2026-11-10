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
@Table(name = "units")
public class UnitEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "unit_type", nullable = false, length = 32)
    private String unitType;

    @Column(name = "base_unit_id")
    private UUID baseUnitId;

    @Column(name = "conversion_factor", nullable = false, precision = 14, scale = 6)
    private BigDecimal conversionFactor = BigDecimal.ONE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UnitEntity() {
    }

    public UnitEntity(UUID id, UUID tenantId, String code, String name, String unitType) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.unitType = unitType;
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
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getUnitType() { return unitType; }
    public UUID getBaseUnitId() { return baseUnitId; }
    public BigDecimal getConversionFactor() { return conversionFactor; }
    public long getVersion() { return version; }

    public void setCode(String code) { this.code = code; }
    public void setName(String name) { this.name = name; }
    public void setUnitType(String unitType) { this.unitType = unitType; }
    public void setBaseUnitId(UUID baseUnitId) { this.baseUnitId = baseUnitId; }
    public void setConversionFactor(BigDecimal conversionFactor) { this.conversionFactor = conversionFactor; }
}
