package com.restaurantpos.domain.kitchen;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.Objects;
import java.util.UUID;

public record KitchenStation(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        String code,
        String name,
        String stationType,
        UUID printerDeviceId,
        int displayOrder,
        int expectedPrepMinutes,
        boolean expoEnabled,
        int paperWidth,
        String receiptLanguage,
        String receiptEncoding,
        boolean defaultStation,
        boolean active,
        long version
) {
    public KitchenStation {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(branchId);
        Objects.requireNonNull(code);
        Objects.requireNonNull(name);
    }
}
