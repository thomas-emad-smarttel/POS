package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

    interface OrderSummaryProjection {
        UUID getId();
        String getOrderNumber();
        String getOrderType();
        String getStatus();
        UUID getDiningTableId();
        BigDecimal getTotalAmount();
        boolean isHeld();
        Instant getOpenedAt();
        long getVersion();
    }

    Optional<OrderEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);

    @Query("""
            SELECT o.id AS id,
                   o.orderNumber AS orderNumber,
                   o.orderType AS orderType,
                   o.status AS status,
                   o.diningTableId AS diningTableId,
                   o.totalAmount AS totalAmount,
                   o.held AS held,
                   o.openedAt AS openedAt,
                   o.version AS version
            FROM OrderEntity o
            WHERE o.tenantId = :tenantId
              AND o.branchId = :branchId
            ORDER BY o.openedAt DESC
            """)
    Page<OrderSummaryProjection> findSummariesByTenantAndBranch(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            Pageable pageable
    );

    @Query("""
            SELECT o.id AS id,
                   o.orderNumber AS orderNumber,
                   o.orderType AS orderType,
                   o.status AS status,
                   o.diningTableId AS diningTableId,
                   o.totalAmount AS totalAmount,
                   o.held AS held,
                   o.openedAt AS openedAt,
                   o.version AS version
            FROM OrderEntity o
            WHERE o.tenantId = :tenantId
              AND o.branchId = :branchId
              AND (:applyStatus = false OR o.status = :status)
              AND (:applyHeld = false OR o.held = :held)
            ORDER BY o.openedAt DESC
            """)
    Page<OrderSummaryProjection> searchSummaries(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            @Param("applyStatus") boolean applyStatus,
            @Param("status") String status,
            @Param("applyHeld") boolean applyHeld,
            @Param("held") boolean held,
            Pageable pageable
    );
}
