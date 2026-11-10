package com.restaurantpos.shared.dto.security;

import java.time.Instant;
import java.util.UUID;

public record SupervisorApprovalResponse(
        UUID approvalId,
        UUID requestedByUserId,
        UUID approvedByUserId,
        String status,
        Instant expiresAt
) {
}
