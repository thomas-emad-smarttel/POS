package com.restaurantpos.domain.kitchen;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KitchenTicketItem(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID kitchenTicketId,
        UUID orderItemId,
        String itemNameSnapshot,
        String variantNameSnapshot,
        BigDecimal quantity,
        List<String> selectedModifiers,
        String prepInstructions,
        String notes,
        int expectedPrepMinutes,
        KitchenTicketItemStatus status,
        Instant startedAt,
        Instant readyAt,
        Instant servedAt,
        String cancelReason,
        long version
) {
}
