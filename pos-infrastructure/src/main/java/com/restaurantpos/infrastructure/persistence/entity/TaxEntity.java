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
@Table(name = "taxes")
public class TaxEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "rate_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal ratePercent;

    @Column(name = "inclusive", nullable = false)
    private boolean inclusive;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TaxEntity() {
    }

    public TaxEntity(UUID id, UUID tenantId, String code, String name, BigDecimal ratePercent, boolean inclusive, boolean active) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.ratePercent = ratePercent;
        this.inclusive = inclusive;
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
    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public boolean isInclusive() { return inclusive; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
    public void setName(String name) { this.name = name; }
    public void setRatePercent(BigDecimal ratePercent) { this.ratePercent = ratePercent; }
    public void setInclusive(boolean inclusive) { this.inclusive = inclusive; }
    public void setActive(boolean active) { this.active = active; }
}
