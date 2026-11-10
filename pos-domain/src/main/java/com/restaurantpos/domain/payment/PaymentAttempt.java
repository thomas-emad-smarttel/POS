package com.restaurantpos.domain.payment;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks payment processing lifecycle (PENDING -> PROCESSING -> COMPLETED/FAILED/CANCELLED/UNCERTAIN/REFUNDED)
 * and safe external payment terminal references without modifying append-only financial entries.
 */
public record PaymentAttempt(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID orderId,
        UUID paymentMethodId,
        UUID cashSessionId,
        UUID initiatedByUserId,
        UUID linkedPaymentId,
        String idempotencyKey,
        String currencyCode,
        BigDecimal requestedAmount,
        BigDecimal tenderedAmount,
        BigDecimal changeAmount,
        BigDecimal tipAmount,
        PaymentAttemptStatus status,
        String providerName,
        String providerTxId,
        String terminalRef,
        String maskedPan,
        String cardBrand,
        String authCode,
        String responseCode,
        String failureReason,
        boolean reconciled,
        Instant reconciledAt,
        UUID reconciledByUserId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public PaymentAttempt {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(branchId);
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(paymentMethodId);
        Objects.requireNonNull(initiatedByUserId);
        Objects.requireNonNull(idempotencyKey);
        Objects.requireNonNull(status);
    }
}
