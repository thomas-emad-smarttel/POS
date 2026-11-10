package com.restaurantpos.shared.dto.kitchen;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record KitchenTicketActionRequest(
        long expectedVersion,
        @Size(max = 300) String reason,
        UUID supervisorApprovalId
) {
}
