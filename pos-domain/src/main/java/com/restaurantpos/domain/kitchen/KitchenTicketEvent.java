package com.restaurantpos.domain.kitchen;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.UUID;

public record KitchenTicketEvent(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID kitchenTicketId,
        UUID kitchenTicketItemId,
        UUID actorUserId,
        String eventType,
        String previousStatus,
        String newStatus,
        String reason,
        String detailsJson,
        Instant occurredAt
) {
}
