package com.restaurantpos.domain.kitchen;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.UUID;

public record KitchenDisplayConfiguration(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        boolean expoEnabled,
        String defaultSortMode,
        int defaultExpectedPrepMinutes,
        boolean autoPrintOnDispatch,
        long version
) {
}
