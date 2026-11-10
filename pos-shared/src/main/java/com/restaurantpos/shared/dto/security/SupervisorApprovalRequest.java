package com.restaurantpos.shared.dto.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SupervisorApprovalRequest(
        @NotBlank @Size(max = 100) String supervisorUsername,
        @NotBlank @Size(max = 128) String supervisorPassword,
        @NotBlank @Size(max = 80) String requiredPermission,
        @NotBlank @Size(max = 80) String targetEntityType,
        @NotBlank @Size(max = 120) String targetEntityId,
        @NotBlank @Size(max = 300) String reason,
        UUID terminalId
) {
}
