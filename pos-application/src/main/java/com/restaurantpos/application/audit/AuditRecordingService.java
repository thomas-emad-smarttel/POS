package com.restaurantpos.application.audit;

import com.restaurantpos.domain.port.AuditLogPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AuditEntry;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.AuditAction;

import java.util.Objects;
import java.util.UUID;

/**
 * Records security-sensitive events through the domain audit port without logging secrets.
 */
public class AuditRecordingService {

    private final AuditLogPort auditLogPort;
    private final SystemClockPort clockPort;

    public AuditRecordingService(AuditLogPort auditLogPort, SystemClockPort clockPort) {
        this.auditLogPort = Objects.requireNonNull(auditLogPort, "auditLogPort must not be null");
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort must not be null");
    }

    public AuditEntry record(
            TenantId tenantId,
            BranchId branchId,
            UUID actorUserId,
            AuditAction action,
            String targetEntityType,
            String targetEntityId,
            String detailsJson,
            CorrelationId correlationId
    ) {
        AuditEntry entry = new AuditEntry(
                UUID.randomUUID(),
                tenantId,
                branchId,
                actorUserId,
                action,
                targetEntityType,
                targetEntityId,
                detailsJson,
                correlationId,
                clockPort.now()
        );
        auditLogPort.record(entry);
        return entry;
    }
}
