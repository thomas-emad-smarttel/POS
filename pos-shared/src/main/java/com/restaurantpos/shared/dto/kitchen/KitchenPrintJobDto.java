package com.restaurantpos.shared.dto.kitchen;

import java.time.Instant;
import java.util.UUID;

public record KitchenPrintJobDto(
        UUID id,
        UUID kitchenTicketId,
        UUID kitchenStationId,
        String jobType,
        String printerName,
        String language,
        int paperWidth,
        String renderedText,
        String status,
        int attemptCount,
        String errorMessage,
        Instant createdAt,
        Instant lastAttemptAt
) {
}
