package com.restaurantpos.domain.port;

import com.restaurantpos.domain.security.AuditEntry;

/**
 * Outbound domain port for persisting immutable security audit entries.
 */
public interface AuditLogPort {
    void record(AuditEntry entry);
}
