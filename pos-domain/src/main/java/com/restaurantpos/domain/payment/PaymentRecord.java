package com.restaurantpos.domain.payment;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only payment settlement ledger entry (never stores PAN, CVV, PIN, or track data).
 */
public record PaymentRecord(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID orderId,
        UUID cashSessionId,
        UUID paymentMethodId,
        UUID processedByUserId,
        String idempotencyKey,
        String currencyCode,
        BigDecimal amount,
        BigDecimal tenderedAmount,
        BigDecimal changeAmount,
        BigDecimal tipAmount,
        String status,
        String externalReference,
        String maskedAccount,
        Instant paidAt
) {
    public PaymentRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(branchId, "branchId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(paymentMethodId, "paymentMethodId must not be null");
        Objects.requireNonNull(processedByUserId, "processedByUserId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    }

    public PaymentRecord(
            UUID id,
            TenantId tenantId,
            BranchId branchId,
            UUID orderId,
            UUID paymentMethodId,
            UUID processedByUserId,
            String idempotencyKey,
            String currencyCode,
            BigDecimal amount,
            BigDecimal tenderedAmount,
            BigDecimal changeAmount,
            BigDecimal tipAmount,
            String status,
            Instant paidAt
    ) {
        this(id, tenantId, branchId, orderId, null, paymentMethodId, processedByUserId,
                idempotencyKey, currencyCode, amount, tenderedAmount, changeAmount, tipAmount,
                status, null, null, paidAt);
    }
}
