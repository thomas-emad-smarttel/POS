package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.BranchCategoryOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchCategoryOverrideJpaRepository extends JpaRepository<BranchCategoryOverrideEntity, UUID> {
    Optional<BranchCategoryOverrideEntity> findByTenantIdAndBranchIdAndCategoryId(UUID tenantId, UUID branchId, UUID categoryId);
    List<BranchCategoryOverrideEntity> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);
}
