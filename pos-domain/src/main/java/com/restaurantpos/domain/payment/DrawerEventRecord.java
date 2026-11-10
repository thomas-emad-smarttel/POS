package com.restaurantpos.domain.payment;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.UUID;

public record DrawerEventRecord(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID terminalId,
        UUID cashSessionId,
        UUID orderId,
        UUID paymentId,
        UUID actorUserId,
        UUID approvalId,
        String triggerType,
        String reason,
        String hardwareDeviceName,
        String hardwareStatus,
        boolean openedOk,
        String errorMessage,
        String correlationId,
        Instant occurredAt
) {
}
