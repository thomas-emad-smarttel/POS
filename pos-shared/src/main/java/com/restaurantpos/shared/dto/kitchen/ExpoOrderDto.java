package com.restaurantpos.shared.dto.kitchen;

import java.time.Instant;
import java.util.List;

public record ExpoOrderDto(
        java.util.UUID orderId,
        String orderNumber,
        String orderType,
        String tableLabel,
        String aggregateStatus,
        boolean allStationsReady,
        int readyTicketCount,
        int totalTicketCount,
        List<String> pendingStations,
        Instant earliestFiredAt,
        long maxElapsedSeconds,
        boolean anyOverdue,
        List<KitchenTicketDto> stationTickets
) {
}
