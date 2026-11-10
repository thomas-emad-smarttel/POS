package com.restaurantpos.application;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.port.AuditLogPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.security.AuditEntry;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RbacAndAuditApplicationTest {

    @Test
    void permissionPolicyEnforcesTenantBranchAndGranularPermission() {
        TenantId tenantA = TenantId.of(UUID.randomUUID());
        TenantId tenantB = TenantId.of(UUID.randomUUID());
        BranchId branch1 = BranchId.of(UUID.randomUUID());
        BranchId branch2 = BranchId.of(UUID.randomUUID());

        AccessPrincipal cashier = new AccessPrincipal(
                UUID.randomUUID(),
                "cashier1",
                tenantA,
                Set.of(branch1),
                Set.of(PermissionCode.ORDERS_CREATE, PermissionCode.PAYMENTS_CREATE),
                true
        );

        assertDoesNotThrow(() ->
                PermissionPolicy.requireBranchPermission(cashier, tenantA, branch1, PermissionCode.ORDERS_CREATE));

        assertThrows(UnauthorizedOperationException.class, () ->
                PermissionPolicy.requireBranchPermission(cashier, tenantB, branch1, PermissionCode.ORDERS_CREATE));

        assertThrows(UnauthorizedOperationException.class, () ->
                PermissionPolicy.requireBranchPermission(cashier, tenantA, branch2, PermissionCode.ORDERS_CREATE));

        assertThrows(UnauthorizedOperationException.class, () ->
                PermissionPolicy.requireBranchPermission(cashier, tenantA, branch1, PermissionCode.PAYMENTS_REFUND));
    }

    @Test
    void auditRecordingServiceCapturesTimestampedEntry() {
        List<AuditEntry> captured = new ArrayList<>();
        AuditLogPort port = captured::add;
        Instant fixedNow = Instant.parse("2026-11-10T12:00:00Z");
        SystemClockPort clock = () -> fixedNow;

        AuditRecordingService service = new AuditRecordingService(port, clock);
        TenantId tenant = TenantId.of(UUID.randomUUID());
        BranchId branch = BranchId.of(UUID.randomUUID());
        UUID actor = UUID.randomUUID();
        CorrelationId corr = CorrelationId.generate();

        AuditEntry entry = service.record(
                tenant,
                branch,
                actor,
                AuditAction.CASH_DRAWER_OPENED,
                "CashDrawer",
                "drawer-1",
                "{\"reason\":\"change\"}",
                corr
        );

        assertEquals(1, captured.size());
        assertEquals(fixedNow, entry.occurredAt());
        assertEquals(AuditAction.CASH_DRAWER_OPENED, entry.action());
    }
}
