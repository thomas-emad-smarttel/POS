package com.restaurantpos.infrastructure.audit;

import com.restaurantpos.domain.port.AuditLogPort;
import com.restaurantpos.domain.security.AuditEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback structured SLF4J audit sink active only when {@code pos.persistence.enabled=false} (e.g. dev no-DB profile).
 */
@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "false")
public class StructuredAuditLogAdapter implements AuditLogPort {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Override
    public void record(AuditEntry entry) {
        AUDIT_LOG.info(
                "auditId={} tenantId={} branchId={} actorUserId={} action={} targetType={} targetId={} correlationId={} occurredAt={}",
                entry.id(),
                entry.tenantId(),
                entry.branchId(),
                entry.actorUserId(),
                entry.action(),
                entry.targetEntityType(),
                entry.targetEntityId(),
                entry.correlationId(),
                entry.occurredAt()
        );
    }
}
