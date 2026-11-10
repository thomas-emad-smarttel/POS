package com.restaurantpos.domain.security;

import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.AuditAction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable security and compliance audit record. Secrets and cardholder data must never be included.
 */
public record AuditEntry(
        UUID id,
        TenantId tenantId,
        BranchId branchId,
        UUID actorUserId,
        AuditAction action,
        String targetEntityType,
        String targetEntityId,
        String detailsJson,
        CorrelationId correlationId,
        Instant occurredAt
) {
    public AuditEntry {
        Objects.requireNonNull(id, "audit id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
