package com.restaurantpos.domain.port;

import com.restaurantpos.domain.tenancy.Tenant;
import com.restaurantpos.shared.model.TenantId;

import java.util.Optional;

/**
 * Outbound persistence port for tenant aggregates.
 */
public interface TenantRepositoryPort {
    Tenant save(Tenant tenant);

    Optional<Tenant> findById(TenantId tenantId);
}
