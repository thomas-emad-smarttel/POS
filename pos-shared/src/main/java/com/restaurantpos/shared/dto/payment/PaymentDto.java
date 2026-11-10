package com.restaurantpos.shared.dto.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only payment settlement DTO containing only safe non-sensitive provider metadata.
 */
public record PaymentDto(
        UUID id,
        UUID orderId,
        UUID cashSessionId,
        UUID paymentMethodId,
        String paymentMethodCode,
        String paymentMethodType,
        UUID processedByUserId,
        String idempotencyKey,
        String currencyCode,
        BigDecimal amount,
        BigDecimal tenderedAmount,
        BigDecimal changeAmount,
        BigDecimal tipAmount,
        BigDecimal refundedAmount,
        String status,
        String externalReference,
        String maskedAccount,
        Instant paidAt
) {
}
