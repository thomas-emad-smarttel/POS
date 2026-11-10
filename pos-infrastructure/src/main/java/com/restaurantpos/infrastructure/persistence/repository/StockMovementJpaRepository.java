package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.StockMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockMovementJpaRepository extends JpaRepository<StockMovementEntity, UUID> {
}
