package com.restaurantpos.domain.port;

import com.restaurantpos.domain.tenancy.Tenant;

import java.util.Optional;

/**
 * Outbound query port for resolving tenants by business code.
 */
public interface TenantQueryPort {
    Optional<Tenant> findByCode(String code);
}
