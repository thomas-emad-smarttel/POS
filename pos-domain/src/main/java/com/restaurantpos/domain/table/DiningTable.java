package com.restaurantpos.domain.table;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.Objects;
import java.util.UUID;

public record DiningTable(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID diningAreaId,
        String diningAreaName,
        String tableCode,
        String displayName,
        int capacity,
        int sortOrder,
        TableStatus status,
        UUID activeOrderId,
        boolean active,
        long version
) {
    public DiningTable {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(branchId, "branchId must not be null");
        Objects.requireNonNull(diningAreaId, "diningAreaId must not be null");
        Objects.requireNonNull(tableCode, "tableCode must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
