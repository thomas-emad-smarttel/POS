package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRoleAssignmentJpaRepository extends JpaRepository<UserRoleAssignmentEntity, UUID> {
    List<UserRoleAssignmentEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);
    void deleteByTenantIdAndUserId(UUID tenantId, UUID userId);
}
