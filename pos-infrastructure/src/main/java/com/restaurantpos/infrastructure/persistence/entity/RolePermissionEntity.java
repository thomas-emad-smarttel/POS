package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "role_permissions")
public class RolePermissionEntity {

    @Embeddable
    public static class RolePermissionId implements Serializable {
        private static final long serialVersionUID = 1L;

        @Column(name = "role_id", nullable = false)
        private UUID roleId;

        @Column(name = "permission_id", nullable = false)
        private UUID permissionId;

        protected RolePermissionId() {
        }

        public RolePermissionId(UUID roleId, UUID permissionId) {
            this.roleId = roleId;
            this.permissionId = permissionId;
        }

        public UUID getRoleId() { return roleId; }
        public UUID getPermissionId() { return permissionId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RolePermissionId that)) return false;
            return Objects.equals(roleId, that.roleId) && Objects.equals(permissionId, that.permissionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roleId, permissionId);
        }
    }

    @EmbeddedId
    private RolePermissionId id;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    protected RolePermissionEntity() {
    }

    public RolePermissionEntity(UUID roleId, UUID permissionId) {
        this.id = new RolePermissionId(roleId, permissionId);
    }

    @PrePersist
    void onCreate() {
        this.grantedAt = Instant.now();
    }

    public RolePermissionId getId() { return id; }
}
