package com.restaurantpos.shared.dto.security;

import java.util.UUID;

public record UserRoleAssignmentDto(
        UUID roleId,
        String roleCode,
        String roleName,
        UUID branchId
) {
}
