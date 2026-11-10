package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.KitchenTicketItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface KitchenTicketItemJpaRepository extends JpaRepository<KitchenTicketItemEntity, UUID> {
    List<KitchenTicketItemEntity> findByKitchenTicketIdOrderByCreatedAtAsc(UUID kitchenTicketId);

    @Query("""
            SELECT DISTINCT i.orderItemId
            FROM KitchenTicketItemEntity i
            JOIN KitchenTicketEntity t ON t.id = i.kitchenTicketId
            WHERE t.orderId = :orderId
              AND t.tenantId = :tenantId
              AND t.branchId = :branchId
            """)
    List<UUID> findDispatchedOrderItemIdsForOrder(
            @Param("orderId") UUID orderId,
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId
    );
}
