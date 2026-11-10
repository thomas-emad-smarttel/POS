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
@Table(name = "kitchen_display_configs")
public class KitchenDisplayConfigEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "expo_enabled", nullable = false)
    private boolean expoEnabled;

    @Column(name = "default_sort_mode", nullable = false, length = 32)
    private String defaultSortMode;

    @Column(name = "default_expected_prep_minutes", nullable = false)
    private int defaultExpectedPrepMinutes;

    @Column(name = "auto_print_on_dispatch", nullable = false)
    private boolean autoPrintOnDispatch;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected KitchenDisplayConfigEntity() {
    }

    public KitchenDisplayConfigEntity(
            UUID id, UUID tenantId, UUID branchId, boolean expoEnabled,
            String defaultSortMode, int defaultExpectedPrepMinutes, boolean autoPrintOnDispatch
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.expoEnabled = expoEnabled;
        this.defaultSortMode = defaultSortMode;
        this.defaultExpectedPrepMinutes = defaultExpectedPrepMinutes;
        this.autoPrintOnDispatch = autoPrintOnDispatch;
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
    public boolean isExpoEnabled() { return expoEnabled; }
    public void setExpoEnabled(boolean expoEnabled) { this.expoEnabled = expoEnabled; }
    public String getDefaultSortMode() { return defaultSortMode; }
    public void setDefaultSortMode(String defaultSortMode) { this.defaultSortMode = defaultSortMode; }
    public int getDefaultExpectedPrepMinutes() { return defaultExpectedPrepMinutes; }
    public void setDefaultExpectedPrepMinutes(int defaultExpectedPrepMinutes) { this.defaultExpectedPrepMinutes = defaultExpectedPrepMinutes; }
    public boolean isAutoPrintOnDispatch() { return autoPrintOnDispatch; }
    public void setAutoPrintOnDispatch(boolean autoPrintOnDispatch) { this.autoPrintOnDispatch = autoPrintOnDispatch; }
    public long getVersion() { return version; }
}
