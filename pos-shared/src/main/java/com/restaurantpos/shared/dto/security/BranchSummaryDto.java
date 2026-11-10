package com.restaurantpos.shared.dto.security;

import java.util.UUID;

public record BranchSummaryDto(
        UUID id,
        String code,
        String name,
        String timezone,
        String currencyCode,
        boolean active
) {
}
