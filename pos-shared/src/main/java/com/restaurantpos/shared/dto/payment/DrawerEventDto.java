package com.restaurantpos.shared.dto.payment;

import java.time.Instant;
import java.util.UUID;

public record DrawerEventDto(
        UUID id,
        UUID branchId,
        UUID terminalId,
        UUID cashSessionId,
        UUID orderId,
        UUID paymentId,
        UUID actorUserId,
        String triggerType,
        String reason,
        String hardwareDeviceName,
        String hardwareStatus,
        boolean openedOk,
        String errorMessage,
        Instant occurredAt
) {
}
