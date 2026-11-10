package com.restaurantpos.domain.port;

import com.restaurantpos.domain.tenancy.Branch;
import com.restaurantpos.shared.model.TenantId;

import java.util.List;

/**
 * Outbound query port for tenant-scoped branch listings.
 */
public interface BranchQueryPort {
    List<Branch> findByTenant(TenantId tenantId);
}
