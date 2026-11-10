package com.restaurantpos.shared.dto.kitchen;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KitchenTicketDto(
        UUID id,
        UUID branchId,
        UUID orderId,
        String orderNumberSnapshot,
        String orderTypeSnapshot,
        String tableLabelSnapshot,
        UUID waiterId,
        String waiterNameSnapshot,
        UUID kitchenStationId,
        String stationCode,
        String stationName,
        String ticketNumber,
        String ticketKind,
        String status,
        String priorityLevel,
        int priorityScore,
        int expectedPrepMinutes,
        long elapsedSeconds,
        boolean overdue,
        Instant firedAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant readyAt,
        Instant bumpedAt,
        Instant cancelledAt,
        String holdReason,
        String cancelReason,
        long version,
        List<KitchenTicketItemDto> items
) {
}
