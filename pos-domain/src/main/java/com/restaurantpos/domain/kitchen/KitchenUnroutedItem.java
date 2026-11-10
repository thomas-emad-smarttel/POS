package com.restaurantpos.domain.kitchen;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record KitchenUnroutedItem(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID outboxEventId,
        UUID orderId,
        String orderNumber,
        UUID orderItemId,
        UUID productId,
        String productNameSnapshot,
        BigDecimal quantity,
        String modifiersSummary,
        String status,
        UUID resolvedStationId,
        UUID resolvedByUserId,
        Instant resolvedAt,
        Instant createdAt
) {
}
