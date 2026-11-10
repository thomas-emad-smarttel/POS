package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.InventoryAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryAlertJpaRepository extends JpaRepository<InventoryAlertEntity, UUID> {
    Optional<InventoryAlertEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);
    List<InventoryAlertEntity> findByTenantIdAndBranchIdOrderByCreatedAtDesc(UUID tenantId, UUID branchId);
    List<InventoryAlertEntity> findByTenantIdAndBranchIdAndStatusInOrderByCreatedAtDesc(
            UUID tenantId, UUID branchId, List<String> statuses);
    Optional<InventoryAlertEntity> findByTenantIdAndBranchIdAndDedupKeyAndStatusIn(
            UUID tenantId, UUID branchId, String dedupKey, List<String> statuses);
}
