package com.restaurantpos.domain.order;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderAggregate(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID terminalId,
        UUID diningTableId,
        UUID createdByUserId,
        UUID assignedWaiterId,
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
        String customerReference,
        String notes,
        boolean held,
        Instant openedAt,
        Instant sentToKitchenAt,
        Instant closedAt,
        long version,
        List<OrderItemDetail> items,
        List<OrderDiscountSnapshot> discounts,
        List<OrderTaxSnapshot> taxes
) {
    public OrderAggregate {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(branchId);
        Objects.requireNonNull(createdByUserId);
        Objects.requireNonNull(orderNumber);
        Objects.requireNonNull(orderType);
        Objects.requireNonNull(status);
        items = items == null ? List.of() : List.copyOf(items);
        discounts = discounts == null ? List.of() : List.copyOf(discounts);
        taxes = taxes == null ? List.of() : List.copyOf(taxes);
    }
}
