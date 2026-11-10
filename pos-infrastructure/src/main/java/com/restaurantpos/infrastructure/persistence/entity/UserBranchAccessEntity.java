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
@Table(name = "user_branch_access")
public class UserBranchAccessEntity {

    @Embeddable
    public static class UserBranchAccessId implements Serializable {
        private static final long serialVersionUID = 1L;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "branch_id", nullable = false)
        private UUID branchId;

        protected UserBranchAccessId() {
        }

        public UserBranchAccessId(UUID userId, UUID branchId) {
            this.userId = userId;
            this.branchId = branchId;
        }

        public UUID getUserId() { return userId; }
        public UUID getBranchId() { return branchId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserBranchAccessId that)) return false;
            return Objects.equals(userId, that.userId) && Objects.equals(branchId, that.branchId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, branchId);
        }
    }

    @EmbeddedId
    private UserBranchAccessId id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    protected UserBranchAccessEntity() {
    }

    public UserBranchAccessEntity(UUID userId, UUID branchId, UUID tenantId) {
        this.id = new UserBranchAccessId(userId, branchId);
        this.tenantId = tenantId;
    }

    @PrePersist
    void onCreate() {
        this.grantedAt = Instant.now();
    }

    public UserBranchAccessId getId() { return id; }
    public UUID getTenantId() { return tenantId; }
}
