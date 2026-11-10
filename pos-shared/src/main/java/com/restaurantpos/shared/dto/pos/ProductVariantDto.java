package com.restaurantpos.shared.dto.pos;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantDto(
        UUID id,
        UUID productId,
        String sku,
        String barcode,
        String name,
        BigDecimal priceOverride,
        int displayOrder,
        boolean active,
        long version
) {
}
