package com.restaurantpos.domain.payment;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Separate append-only refund transaction linked to its original payment and order.
 */
public record RefundRecord(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
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
