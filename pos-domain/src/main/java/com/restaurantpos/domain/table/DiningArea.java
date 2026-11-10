package com.restaurantpos.domain.table;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.Objects;
import java.util.UUID;

public record DiningArea(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        String code,
        String name,
        int displayOrder,
        boolean active,
        long version
) {
    public DiningArea {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(branchId, "branchId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
