package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "color_hex", length = 16)
    private String colorHex;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CategoryEntity() {
    }

    public CategoryEntity(UUID id, UUID tenantId, String code, String name, int displayOrder, boolean active) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
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
    public UUID getParentId() { return parentId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getDisplayOrder() { return displayOrder; }
    public String getColorHex() { return colorHex; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }

    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public void setCode(String code) { this.code = code; }
    public void setName(String name) { this.name = name; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public void setActive(boolean active) { this.active = active; }
}
