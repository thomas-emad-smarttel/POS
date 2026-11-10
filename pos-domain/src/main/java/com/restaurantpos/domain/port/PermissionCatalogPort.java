package com.restaurantpos.domain.port;

import com.restaurantpos.domain.security.PermissionDefinition;

import java.util.List;

/**
 * Outbound port managing synchronization and retrieval of the persisted permission catalog.
 */
public interface PermissionCatalogPort {
    void ensureCatalogSynced();

    List<PermissionDefinition> findAll();
}
