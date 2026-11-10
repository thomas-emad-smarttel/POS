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
@Table(name = "modifier_groups")
public class ModifierGroupEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "min_selections", nullable = false)
    private int minSelections;

    @Column(name = "max_selections", nullable = false)
    private int maxSelections;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ModifierGroupEntity() {
    }

    public ModifierGroupEntity(UUID id, UUID tenantId, String code, String name, int minSelections, int maxSelections, boolean required, boolean active) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.minSelections = minSelections;
        this.maxSelections = maxSelections;
        this.required = required;
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
    public int getMinSelections() { return minSelections; }
    public int getMaxSelections() { return maxSelections; }
    public boolean isRequired() { return required; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
    public void setName(String name) { this.name = name; }
    public void setMinSelections(int minSelections) { this.minSelections = minSelections; }
    public void setMaxSelections(int maxSelections) { this.maxSelections = maxSelections; }
    public void setRequired(boolean required) { this.required = required; }
    public void setActive(boolean active) { this.active = active; }
}
