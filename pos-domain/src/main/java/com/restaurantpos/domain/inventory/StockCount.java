package com.restaurantpos.domain.inventory;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockCount(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        String countNumber,
        UUID stockLocationId,
        UUID categoryId,
        String status,
        UUID createdByUserId,
        UUID submittedByUserId,
        UUID approvedByUserId,
        UUID postedByUserId,
        UUID approvalId,
        BigDecimal totalVarianceCost,
        String notes,
        Instant submittedAt,
        Instant approvedAt,
        Instant postedAt,
        long version,
        List<StockCountItem> items
) {
}
