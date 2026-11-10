package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kitchen_station_assignments")
public class KitchenStationAssignmentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "kitchen_station_id", nullable = false)
    private UUID kitchenStationId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "terminal_id")
    private UUID terminalId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KitchenStationAssignmentEntity() {
    }

    public KitchenStationAssignmentEntity(
            UUID id, UUID tenantId, UUID branchId, UUID kitchenStationId,
            UUID userId, UUID terminalId, boolean active
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.kitchenStationId = kitchenStationId;
        this.userId = userId;
        this.terminalId = terminalId;
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
    public UUID getUserId() { return userId; }
    public UUID getTerminalId() { return terminalId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
