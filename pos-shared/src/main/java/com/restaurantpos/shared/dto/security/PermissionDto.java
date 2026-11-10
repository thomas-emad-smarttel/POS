package com.restaurantpos.shared.dto.security;

import java.util.UUID;

public record PermissionDto(
        UUID id,
        String code,
        String module,
        String description,
        boolean sensitive
) {
}
