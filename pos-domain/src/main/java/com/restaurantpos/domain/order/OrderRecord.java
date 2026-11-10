package com.restaurantpos.domain.order;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain aggregate view of an order and its snapshot items.
 */
public record OrderRecord(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID createdByUserId,
        String orderNumber,
        OrderType orderType,
        OrderStatus status,
        String currencyCode,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        int guestCount,
        Instant openedAt,
        long version,
        List<OrderItemSnapshot> items
) {
    public OrderRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(branchId, "branchId must not be null");
        Objects.requireNonNull(createdByUserId, "createdByUserId must not be null");
        Objects.requireNonNull(orderNumber, "orderNumber must not be null");
        Objects.requireNonNull(orderType, "orderType must not be null");
        Objects.requireNonNull(status, "status must not be null");
        items = items == null ? List.of() : List.copyOf(items);
    }

    public OrderRecord withStatus(OrderStatus newStatus) {
        return new OrderRecord(
                id, tenantId, branchId, createdByUserId, orderNumber, orderType, newStatus,
                currencyCode, subtotalAmount, discountAmount, taxAmount, totalAmount, paidAmount,
                guestCount, openedAt, version, items
        );
    }
}
