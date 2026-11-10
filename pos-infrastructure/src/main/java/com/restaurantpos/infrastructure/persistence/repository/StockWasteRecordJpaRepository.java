package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.StockWasteRecordEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockWasteRecordJpaRepository extends JpaRepository<StockWasteRecordEntity, UUID> {
    Optional<StockWasteRecordEntity> findByTenantIdAndBranchIdAndIdempotencyKey(UUID tenantId, UUID branchId, String idempotencyKey);
    List<StockWasteRecordEntity> findByTenantIdAndBranchIdOrderByOccurredAtDesc(UUID tenantId, UUID branchId, Pageable pageable);
}
