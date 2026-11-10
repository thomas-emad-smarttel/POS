package com.restaurantpos.shared.dto.pos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDto(
        UUID id,
        UUID tenantId,
        UUID branchId,
        UUID terminalId,
        UUID diningTableId,
        UUID createdByUserId,
        UUID assignedWaiterId,
        String orderNumber,
        String orderType,
        String status,
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
        List<OrderItemDto> items,
        List<OrderDiscountDto> discounts,
        List<OrderTaxDto> taxes
) {
}
