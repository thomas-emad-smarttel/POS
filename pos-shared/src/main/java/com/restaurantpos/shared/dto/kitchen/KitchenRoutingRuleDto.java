package com.restaurantpos.shared.dto.kitchen;

import java.util.UUID;

public record KitchenRoutingRuleDto(
        UUID id,
        UUID branchId,
        UUID kitchenStationId,
        String stationCode,
        String stationName,
        UUID productId,
        UUID categoryId,
        String ruleScope,
        int precedence,
        Integer expectedPrepMinutesOverride,
        boolean active,
        long version
) {
}
