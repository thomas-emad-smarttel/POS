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
@Table(name = "modifiers")
public class ModifierEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "modifier_group_id", nullable = false, updatable = false)
    private UUID modifierGroupId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "price_delta", nullable = false, precision = 14, scale = 4)
    private BigDecimal priceDelta;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "default_selected", nullable = false)
    private boolean defaultSelected;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ModifierEntity() {
    }

    public ModifierEntity(UUID id, UUID tenantId, UUID modifierGroupId, String code, String name, BigDecimal priceDelta, int displayOrder, boolean defaultSelected, boolean active) {
        this.id = id;
        this.tenantId = tenantId;
        this.modifierGroupId = modifierGroupId;
        this.code = code;
        this.name = name;
        this.priceDelta = priceDelta;
        this.displayOrder = displayOrder;
        this.defaultSelected = defaultSelected;
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
    public UUID getModifierGroupId() { return modifierGroupId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getPriceDelta() { return priceDelta; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isDefaultSelected() { return defaultSelected; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
    public void setName(String name) { this.name = name; }
    public void setPriceDelta(BigDecimal priceDelta) { this.priceDelta = priceDelta; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public void setDefaultSelected(boolean defaultSelected) { this.defaultSelected = defaultSelected; }
    public void setActive(boolean active) { this.active = active; }
}
