package com.restaurantpos.domain.port;

import com.restaurantpos.domain.tenancy.Branch;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.Optional;

/**
 * Outbound persistence port for tenant-isolated branch entities.
 */
public interface BranchRepositoryPort {
    Branch save(Branch branch);

    Optional<Branch> findByIdAndTenant(BranchId branchId, TenantId tenantId);
}
