package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.StockMovementEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockMovementJpaRepository extends JpaRepository<StockMovementEntity, UUID> {
    Optional<StockMovementEntity> findByTenantIdAndBranchIdAndIdempotencyKey(UUID tenantId, UUID branchId, String idempotencyKey);
    List<StockMovementEntity> findByTenantIdAndBranchIdOrderByOccurredAtDesc(UUID tenantId, UUID branchId, Pageable pageable);
    List<StockMovementEntity> findByTenantIdAndBranchIdAndIngredientIdOrderByOccurredAtDesc(
            UUID tenantId, UUID branchId, UUID ingredientId, Pageable pageable);
}
