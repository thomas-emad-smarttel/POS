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
@Table(name = "kitchen_routing_rules")
public class KitchenRoutingRuleEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "kitchen_station_id", nullable = false)
    private UUID kitchenStationId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "precedence", nullable = false)
    private int precedence;

    @Column(name = "expected_prep_minutes_override")
    private Integer expectedPrepMinutesOverride;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected KitchenRoutingRuleEntity() {
    }

    public KitchenRoutingRuleEntity(
            UUID id, UUID tenantId, UUID branchId, UUID kitchenStationId,
            UUID productId, UUID categoryId, int precedence,
            Integer expectedPrepMinutesOverride, boolean active
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.kitchenStationId = kitchenStationId;
        this.productId = productId;
        this.categoryId = categoryId;
        this.precedence = precedence;
        this.expectedPrepMinutesOverride = expectedPrepMinutesOverride;
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
    public UUID getBranchId() { return branchId; }
    public UUID getKitchenStationId() { return kitchenStationId; }
    public void setKitchenStationId(UUID kitchenStationId) { this.kitchenStationId = kitchenStationId; }
    public UUID getProductId() { return productId; }
    public UUID getCategoryId() { return categoryId; }
    public int getPrecedence() { return precedence; }
    public void setPrecedence(int precedence) { this.precedence = precedence; }
    public Integer getExpectedPrepMinutesOverride() { return expectedPrepMinutesOverride; }
    public void setExpectedPrepMinutesOverride(Integer expectedPrepMinutesOverride) { this.expectedPrepMinutesOverride = expectedPrepMinutesOverride; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getVersion() { return version; }
}
