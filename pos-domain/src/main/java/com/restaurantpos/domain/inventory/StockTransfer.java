package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockTransfer(
        UUID id,
        TenantId tenantId,
        String transferNumber,
        BranchId sourceBranchId,
        UUID sourceLocationId,
        BranchId destinationBranchId,
        UUID destinationLocationId,
        String status,
        UUID requestedByUserId,
        UUID approvedByUserId,
        UUID dispatchedByUserId,
        UUID receivedByUserId,
        String notes,
        Instant requestedAt,
        Instant approvedAt,
        Instant dispatchedAt,
        Instant receivedAt,
        long version,
        List<StockTransferItem> items
) {
}
