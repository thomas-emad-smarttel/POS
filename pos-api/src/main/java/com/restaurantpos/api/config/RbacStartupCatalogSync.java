package com.restaurantpos.api.config;

import com.restaurantpos.domain.port.PermissionCatalogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Synchronizes the global permission catalog idempotently on Local Server startup without altering customized roles.
 */
@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class RbacStartupCatalogSync implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RbacStartupCatalogSync.class);

    private final PermissionCatalogPort permissionCatalogPort;

    public RbacStartupCatalogSync(PermissionCatalogPort permissionCatalogPort) {
        this.permissionCatalogPort = permissionCatalogPort;
    }

    @Override
    public void run(ApplicationArguments args) {
        permissionCatalogPort.ensureCatalogSynced();
        LOG.info("RBAC permission catalog synchronized");
    }
}
