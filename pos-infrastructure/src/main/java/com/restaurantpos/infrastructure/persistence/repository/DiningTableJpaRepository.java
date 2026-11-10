package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.DiningTableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiningTableJpaRepository extends JpaRepository<DiningTableEntity, UUID> {
    Optional<DiningTableEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);
    List<DiningTableEntity> findByTenantIdAndBranchIdOrderBySortOrderAscTableCodeAsc(UUID tenantId, UUID branchId);
}
