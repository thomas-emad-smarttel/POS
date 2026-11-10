package com.restaurantpos.shared.dto.security;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateRolePermissionsRequest(
        @NotNull Set<String> permissionCodes
) {
}
