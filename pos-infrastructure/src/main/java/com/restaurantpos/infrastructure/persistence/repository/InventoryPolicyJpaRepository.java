package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.InventoryPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryPolicyJpaRepository extends JpaRepository<InventoryPolicyEntity, UUID> {
    Optional<InventoryPolicyEntity> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);
}
