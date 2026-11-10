package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.UserBranchAccessEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserBranchAccessJpaRepository extends JpaRepository<UserBranchAccessEntity, UserBranchAccessEntity.UserBranchAccessId> {
    List<UserBranchAccessEntity> findByIdUserIdAndTenantId(UUID userId, UUID tenantId);
    void deleteByIdUserIdAndTenantId(UUID userId, UUID tenantId);
}
