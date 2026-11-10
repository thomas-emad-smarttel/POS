package com.restaurantpos.shared.dto.security;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UserProfileDto(
        UUID userId,
        UUID tenantId,
        UUID activeBranchId,
        UUID terminalId,
        UUID sessionId,
        String username,
        String fullName,
        String email,
        boolean active,
        List<UUID> allowedBranchIds,
        Set<String> assignedRoles,
        Set<String> effectivePermissions,
        Instant sessionExpiresAt
) {
}
