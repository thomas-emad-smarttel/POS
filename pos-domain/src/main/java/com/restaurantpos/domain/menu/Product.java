package com.restaurantpos.domain.menu;

import com.restaurantpos.shared.model.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Product(
        UUID id,
        TenantId tenantId,
        UUID categoryId,
        UUID taxId,
        String sku,
        String barcode,
        String name,
        String description,
        BigDecimal basePrice,
        String pricingMode,
        String productType,
        String imageRef,
        int displayOrder,
        boolean active,
        List<UUID> modifierGroupIds,
        long version
) {
    public Product {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(categoryId);
        Objects.requireNonNull(sku);
        Objects.requireNonNull(name);
        Objects.requireNonNull(basePrice);
        modifierGroupIds = modifierGroupIds == null ? List.of() : List.copyOf(modifierGroupIds);
    }
}
