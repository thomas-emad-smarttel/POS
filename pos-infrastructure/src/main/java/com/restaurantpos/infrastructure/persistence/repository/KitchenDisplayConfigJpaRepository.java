package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.KitchenDisplayConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KitchenDisplayConfigJpaRepository extends JpaRepository<KitchenDisplayConfigEntity, UUID> {
    Optional<KitchenDisplayConfigEntity> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);
}
