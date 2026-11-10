package com.restaurantpos.domain.kitchen;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.UUID;

public record KitchenPrintJob(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID kitchenTicketId,
        UUID kitchenStationId,
        String jobType,
        String printerName,
        String language,
        int paperWidth,
        String encoding,
        String renderedText,
        String escPosBase64,
        String status,
        int attemptCount,
        String errorMessage,
        String idempotencyKey,
        Instant createdAt,
        Instant lastAttemptAt
) {
}
