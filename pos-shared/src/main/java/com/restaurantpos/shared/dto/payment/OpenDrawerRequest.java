package com.restaurantpos.shared.dto.payment;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record OpenDrawerRequest(
        UUID cashSessionId,
        UUID terminalId,
        @Size(max = 300) String reason,
        UUID supervisorApprovalId
) {
}
