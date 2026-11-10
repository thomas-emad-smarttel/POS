package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.BranchProductOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchProductOverrideJpaRepository extends JpaRepository<BranchProductOverrideEntity, UUID> {
    Optional<BranchProductOverrideEntity> findByTenantIdAndBranchIdAndProductId(UUID tenantId, UUID branchId, UUID productId);
    List<BranchProductOverrideEntity> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);
}
