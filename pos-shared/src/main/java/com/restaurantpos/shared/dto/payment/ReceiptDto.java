package com.restaurantpos.shared.dto.payment;

import java.time.Instant;
import java.util.UUID;

public record ReceiptDto(
        UUID id,
        UUID branchId,
        UUID orderId,
        UUID paymentId,
        UUID refundId,
        UUID cashSessionId,
        String receiptNumber,
        String receiptType,
        String language,
        int paperWidth,
        String encoding,
        String renderedText,
        int printCount,
        String lastPrintStatus,
        String lastPrintError,
        Instant lastPrintedAt,
        Instant createdAt
) {
}
