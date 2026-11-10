package com.restaurantpos.shared.dto.kitchen;

import java.util.UUID;

public record KitchenStationDto(
        UUID id,
        UUID branchId,
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
}
