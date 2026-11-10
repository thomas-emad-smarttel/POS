package com.restaurantpos.domain.kitchen;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.UUID;

public record KitchenPrinterConfiguration(
        UUID kitchenStationId,
        UUID printerDeviceId,
        int paperWidth,
        String language,
        String encoding
) {
}
