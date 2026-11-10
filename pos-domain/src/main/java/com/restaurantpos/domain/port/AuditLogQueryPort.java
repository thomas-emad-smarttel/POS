package com.restaurantpos.domain.port;

import com.restaurantpos.domain.security.AuditEntry;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.List;

/**
 * Outbound query port for paginated security audit history scoped by tenant and branch.
 */
public interface AuditLogQueryPort {
    List<AuditEntry> findRecentByBranch(TenantId tenantId, BranchId branchId, int page, int size);
}
