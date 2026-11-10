package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_role_assignments")
public class UserRoleAssignmentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "branch_id", updatable = false)
    private UUID branchId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    protected UserRoleAssignmentEntity() {
    }

    public UserRoleAssignmentEntity(UUID id, UUID tenantId, UUID userId, UUID roleId, UUID branchId) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.roleId = roleId;
        this.branchId = branchId;
    }

    @PrePersist
    void onCreate() {
        this.assignedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public UUID getRoleId() { return roleId; }
    public UUID getBranchId() { return branchId; }
}
