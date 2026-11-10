package com.restaurantpos.shared.dto.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserSummaryDto(
        UUID id,
        UUID tenantId,
        String username,
        String fullName,
        String email,
        boolean active,
        Instant lockedUntil,
        Instant lastLoginAt,
        List<UUID> allowedBranchIds,
        List<UserRoleAssignmentDto> roleAssignments
) {
}
