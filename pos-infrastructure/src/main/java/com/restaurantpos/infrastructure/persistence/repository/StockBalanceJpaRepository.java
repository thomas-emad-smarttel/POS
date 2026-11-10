package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.StockBalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockBalanceJpaRepository extends JpaRepository<StockBalanceEntity, UUID> {
    Optional<StockBalanceEntity> findByTenantIdAndBranchIdAndStockLocationIdAndIngredientId(
            UUID tenantId,
            UUID branchId,
            UUID stockLocationId,
            UUID ingredientId
    );
    List<StockBalanceEntity> findByTenantIdAndBranchIdOrderByUpdatedAtDesc(UUID tenantId, UUID branchId);
}
