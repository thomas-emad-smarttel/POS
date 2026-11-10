package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.KitchenUnroutedItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KitchenUnroutedItemJpaRepository extends JpaRepository<KitchenUnroutedItemEntity, UUID> {
    Optional<KitchenUnroutedItemEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);
    List<KitchenUnroutedItemEntity> findByTenantIdAndBranchIdAndStatusOrderByCreatedAtAsc(UUID tenantId, UUID branchId, String status);
}
