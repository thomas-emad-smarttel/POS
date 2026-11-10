package com.restaurantpos.domain.payment;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentTransactionEntry(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID paymentId,
        UUID paymentAttemptId,
        String transactionType,
        BigDecimal amount,
        String gatewayName,
        String gatewayTxRef,
        String authCode,
        String responseCode,
        Instant occurredAt
) {
}
