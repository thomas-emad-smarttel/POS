package com.restaurantpos.domain.kitchen;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Branch-configurable kitchen routing rule.
 * Precedence levels: productId != null (PRODUCT), else categoryId != null (CATEGORY), else (BRANCH_DEFAULT).
 */
public record KitchenRoutingRule(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID kitchenStationId,
        UUID productId,
        UUID categoryId,
        int precedence,
        Integer expectedPrepMinutesOverride,
        boolean active,
        long version
) {
    public KitchenRoutingRule {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(branchId);
        Objects.requireNonNull(kitchenStationId);
    }

    public String scopeName() {
        if (productId != null) return "PRODUCT";
        if (categoryId != null) return "CATEGORY";
        return "BRANCH_DEFAULT";
    }
}
