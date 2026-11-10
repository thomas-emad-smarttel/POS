package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.KitchenStationAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KitchenStationAssignmentJpaRepository extends JpaRepository<KitchenStationAssignmentEntity, UUID> {
    List<KitchenStationAssignmentEntity> findByKitchenStationIdAndTenantIdAndBranchId(
            UUID kitchenStationId, UUID tenantId, UUID branchId);
}
