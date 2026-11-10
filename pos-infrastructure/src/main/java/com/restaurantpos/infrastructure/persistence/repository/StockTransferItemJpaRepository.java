package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.StockTransferItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockTransferItemJpaRepository extends JpaRepository<StockTransferItemEntity, UUID> {
    List<StockTransferItemEntity> findByStockTransferId(UUID stockTransferId);
}
