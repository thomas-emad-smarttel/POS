package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.StockTransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockTransferJpaRepository extends JpaRepository<StockTransferEntity, UUID> {
    Optional<StockTransferEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<StockTransferEntity> findByTenantIdOrderByRequestedAtDesc(UUID tenantId);
}
