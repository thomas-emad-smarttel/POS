package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.StockLocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLocationJpaRepository extends JpaRepository<StockLocationEntity, UUID> {
    List<StockLocationEntity> findByTenantIdAndBranchIdOrderByNameAsc(UUID tenantId, UUID branchId);
    Optional<StockLocationEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);
    Optional<StockLocationEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
