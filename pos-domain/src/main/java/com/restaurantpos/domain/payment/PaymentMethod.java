package com.restaurantpos.domain.payment;

import com.restaurantpos.shared.model.TenantId;

import java.util.Objects;
import java.util.UUID;

public record PaymentMethod(
        UUID id,
        TenantId tenantId,
        String code,
        String name,
        String methodType,
        boolean opensDrawer,
        boolean active,
        long version
) {
    public PaymentMethod {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(code);
        Objects.requireNonNull(name);
        Objects.requireNonNull(methodType);
    }

    public boolean isCash() {
        return "CASH".equalsIgnoreCase(methodType);
    }
}
