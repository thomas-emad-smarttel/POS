package com.restaurantpos.shared.dto.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundDto(
        UUID id,
        UUID orderId,
        UUID originalPaymentId,
        UUID issuedByUserId,
        UUID approvedByUserId,
        String idempotencyKey,
        String currencyCode,
        BigDecimal amount,
        String status,
        String reason,
        String failureReason,
        String externalReference,
        Instant refundedAt
) {
}
