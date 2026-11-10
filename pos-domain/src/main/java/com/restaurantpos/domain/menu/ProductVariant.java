package com.restaurantpos.domain.menu;

import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ProductVariant(
        UUID id,
        TenantId tenantId,
        UUID productId,
        String sku,
        String barcode,
        String name,
        BigDecimal priceOverride,
        int displayOrder,
        boolean active,
        long version
) {
    public ProductVariant {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(productId);
        Objects.requireNonNull(sku);
        Objects.requireNonNull(name);
        Objects.requireNonNull(priceOverride);
    }
}
