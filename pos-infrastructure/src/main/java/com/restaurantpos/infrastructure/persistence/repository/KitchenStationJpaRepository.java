package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.KitchenStationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KitchenStationJpaRepository extends JpaRepository<KitchenStationEntity, UUID> {
    Optional<KitchenStationEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);
    List<KitchenStationEntity> findByTenantIdAndBranchIdOrderByDisplayOrderAscNameAsc(UUID tenantId, UUID branchId);
}
