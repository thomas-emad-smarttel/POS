package com.restaurantpos.shared.dto.pos;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductDto(
        UUID id,
        UUID categoryId,
        String categoryName,
        UUID taxId,
        String sku,
        String barcode,
        String name,
        String description,
        BigDecimal basePrice,
        BigDecimal effectivePrice,
        String pricingMode,
        String productType,
        String imageRef,
        int displayOrder,
        boolean active,
        boolean branchAvailable,
        List<ProductVariantDto> variants,
        List<ModifierGroupDto> modifierGroups,
        long version
) {
}
