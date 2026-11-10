package com.restaurantpos.shared.dto.kitchen;

public record KitchenDisplayConfigDto(
        boolean expoEnabled,
        String defaultSortMode,
        int defaultExpectedPrepMinutes,
        boolean autoPrintOnDispatch
) {
}
