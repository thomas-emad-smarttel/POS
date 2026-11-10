package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemJpaRepository extends JpaRepository<OrderItemEntity, UUID> {
    List<OrderItemEntity> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
