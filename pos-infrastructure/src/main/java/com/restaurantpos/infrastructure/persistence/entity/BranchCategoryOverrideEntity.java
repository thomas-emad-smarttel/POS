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
@Table(name = "branch_category_overrides")
public class BranchCategoryOverrideEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(name = "visible", nullable = false)
    private boolean visible = true;

    @Column(name = "display_order_override")
    private Integer displayOrderOverride;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected BranchCategoryOverrideEntity() {
    }

    public BranchCategoryOverrideEntity(UUID id, UUID tenantId, UUID branchId, UUID categoryId, boolean visible, Integer displayOrderOverride) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.categoryId = categoryId;
        this.visible = visible;
        this.displayOrderOverride = displayOrderOverride;
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
    public UUID getCategoryId() { return categoryId; }
    public boolean isVisible() { return visible; }
    public Integer getDisplayOrderOverride() { return displayOrderOverride; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public void setDisplayOrderOverride(Integer displayOrderOverride) { this.displayOrderOverride = displayOrderOverride; }
}
