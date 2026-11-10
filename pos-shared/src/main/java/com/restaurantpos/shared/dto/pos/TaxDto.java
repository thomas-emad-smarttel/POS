package com.restaurantpos.shared.dto.pos;

import java.math.BigDecimal;
import java.util.UUID;

public record TaxDto(
        UUID id,
        String code,
        String name,
        BigDecimal ratePercent,
        boolean inclusive,
        boolean active,
        long version
) {
}
