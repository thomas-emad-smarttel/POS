package com.restaurantpos.shared.dto.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentAttemptDto(
        UUID id,
        UUID orderId,
        UUID paymentMethodId,
        UUID cashSessionId,
        UUID linkedPaymentId,
        String idempotencyKey,
        String currencyCode,
        BigDecimal requestedAmount,
        BigDecimal tenderedAmount,
        BigDecimal changeAmount,
        BigDecimal tipAmount,
        String status,
        String providerName,
        String providerTxId,
        String terminalRef,
        String maskedPan,
        String cardBrand,
        String authCode,
        String responseCode,
        String failureReason,
        boolean reconciled,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
