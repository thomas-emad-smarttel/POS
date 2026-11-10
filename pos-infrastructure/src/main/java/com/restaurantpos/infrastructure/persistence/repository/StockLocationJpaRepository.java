package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.StockLocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockLocationJpaRepository extends JpaRepository<StockLocationEntity, UUID> {
}
