package com.restaurantpos.domain.menu;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-optimized branch catalog projection merging base product pricing with branch overrides.
 */
public record ProductCatalogView(
        UUID productId,
        String sku,
        String name,
        String categoryName,
        BigDecimal effectivePrice,
        boolean available
) {
}
