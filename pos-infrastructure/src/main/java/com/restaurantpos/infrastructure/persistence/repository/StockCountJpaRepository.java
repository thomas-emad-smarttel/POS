package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.StockCountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockCountJpaRepository extends JpaRepository<StockCountEntity, UUID> {
    Optional<StockCountEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);
    List<StockCountEntity> findByTenantIdAndBranchIdOrderByCreatedAtDesc(UUID tenantId, UUID branchId);
}
