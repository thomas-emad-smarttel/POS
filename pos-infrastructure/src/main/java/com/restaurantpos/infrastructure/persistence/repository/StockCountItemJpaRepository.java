package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.StockCountItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockCountItemJpaRepository extends JpaRepository<StockCountItemEntity, UUID> {
    List<StockCountItemEntity> findByStockCountId(UUID stockCountId);
}
