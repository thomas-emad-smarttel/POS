package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.OrderStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderStatusHistoryJpaRepository extends JpaRepository<OrderStatusHistoryEntity, UUID> {
    List<OrderStatusHistoryEntity> findByOrderIdOrderByChangedAtAsc(UUID orderId);
}
