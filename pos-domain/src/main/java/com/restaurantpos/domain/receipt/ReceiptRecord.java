package com.restaurantpos.domain.receipt;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.UUID;

public record ReceiptRecord(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
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
        String escPosBase64,
        int printCount,
        String lastPrintStatus,
        String lastPrintError,
        Instant lastPrintedAt,
        UUID createdByUserId,
        Instant createdAt
) {
}
