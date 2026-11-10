package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.OrderDiscountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderDiscountJpaRepository extends JpaRepository<OrderDiscountEntity, UUID> {
    List<OrderDiscountEntity> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
    void deleteByOrderId(UUID orderId);
}
