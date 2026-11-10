package com.restaurantpos.domain.kitchen;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KitchenTicket(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID orderId,
        String orderNumberSnapshot,
        String orderTypeSnapshot,
        String tableLabelSnapshot,
        UUID waiterId,
        String waiterNameSnapshot,
        UUID kitchenStationId,
        UUID sourceOutboxEventId,
        String ticketNumber,
        String ticketKind,
        KitchenTicketStatus status,
        KitchenPriorityLevel priorityLevel,
        int priorityScore,
        int expectedPrepMinutes,
        Instant firedAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant readyAt,
        Instant bumpedAt,
        Instant cancelledAt,
        UUID cancelledByUserId,
        String holdReason,
        String cancelReason,
        long version,
        List<KitchenTicketItem> items
) {
}
