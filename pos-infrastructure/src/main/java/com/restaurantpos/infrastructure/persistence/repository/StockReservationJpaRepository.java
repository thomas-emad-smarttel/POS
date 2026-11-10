package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.StockReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockReservationJpaRepository extends JpaRepository<StockReservationEntity, UUID> {
    Optional<StockReservationEntity> findByTenantIdAndBranchIdAndOrderId(UUID tenantId, UUID branchId, UUID orderId);
    List<StockReservationEntity> findByTenantIdAndBranchIdOrderByCreatedAtDesc(UUID tenantId, UUID branchId);
}
