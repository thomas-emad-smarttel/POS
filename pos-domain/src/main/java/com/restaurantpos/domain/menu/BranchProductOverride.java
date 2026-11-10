package com.restaurantpos.domain.menu;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

public record BranchProductOverride(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID productId,
        BigDecimal priceOverride,
        UUID taxOverrideId,
        boolean available,
        long version
) {
}
