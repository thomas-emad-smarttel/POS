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
@Table(name = "stock_locations")
public class StockLocationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "location_type", nullable = false, length = 32)
    private String locationType = "MAIN_STORE";

    @Column(name = "default_location", nullable = false)
    private boolean defaultLocation;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StockLocationEntity() {
    }

    public StockLocationEntity(UUID id, UUID tenantId, UUID branchId, String code, String name, boolean defaultLocation) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.code = code;
        this.name = name;
        this.defaultLocation = defaultLocation;
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
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getLocationType() { return locationType; }
    public boolean isDefaultLocation() { return defaultLocation; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }

    public void setCode(String code) { this.code = code; }
    public void setName(String name) { this.name = name; }
    public void setLocationType(String locationType) { this.locationType = locationType; }
    public void setDefaultLocation(boolean defaultLocation) { this.defaultLocation = defaultLocation; }
    public void setActive(boolean active) { this.active = active; }
}
