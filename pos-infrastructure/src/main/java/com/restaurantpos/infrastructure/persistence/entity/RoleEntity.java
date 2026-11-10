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
@Table(name = "roles")
public class RoleEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "system_template", nullable = false)
    private boolean systemTemplate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "scope_type", nullable = false, length = 24)
    private String scopeType = "BRANCH";

    @Column(name = "max_discount_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxDiscountPct = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RoleEntity() {
    }

    public RoleEntity(
            UUID id,
            UUID tenantId,
            String code,
            String name,
            String description,
            String scopeType,
            boolean systemTemplate,
            boolean active,
            BigDecimal maxDiscountPct
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.scopeType = scopeType;
        this.systemTemplate = systemTemplate;
        this.active = active;
        this.maxDiscountPct = maxDiscountPct;
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
    public String getDescription() { return description; }
    public boolean isSystemTemplate() { return systemTemplate; }
    public boolean isActive() { return active; }
    public String getScopeType() { return scopeType; }
    public BigDecimal getMaxDiscountPct() { return maxDiscountPct; }
    public long getVersion() { return version; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setActive(boolean active) { this.active = active; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public void setMaxDiscountPct(BigDecimal maxDiscountPct) { this.maxDiscountPct = maxDiscountPct; }
}
