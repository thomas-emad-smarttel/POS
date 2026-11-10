package com.restaurantpos.shared.dto.security;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record RoleDto(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        String scopeType,
        boolean systemTemplate,
        boolean active,
        BigDecimal maxDiscountPct,
        Set<String> permissionCodes,
        long version
) {
}
