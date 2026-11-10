package com.restaurantpos.infrastructure.audit;

import com.restaurantpos.domain.port.AuditLogPort;
import com.restaurantpos.domain.port.AuditLogQueryPort;
import com.restaurantpos.domain.security.AuditEntry;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.AuditLogEntity;
import com.restaurantpos.infrastructure.persistence.repository.AuditLogJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.AuditAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PostgreSQL-backed implementation of {@link AuditLogPort} and {@link AuditLogQueryPort}.
 */
@Component
@Primary
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class JpaAuditLogAdapter implements AuditLogPort, AuditLogQueryPort {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final AuditLogJpaRepository repository;
    private final DatabaseExceptionTranslator exceptionTranslator;

    public JpaAuditLogAdapter(AuditLogJpaRepository repository, DatabaseExceptionTranslator exceptionTranslator) {
        this.repository = repository;
        this.exceptionTranslator = exceptionTranslator;
    }

    @Override
    @Transactional
    public void record(AuditEntry entry) {
        String sanitizedJson = AuditPayloadSanitizer.sanitizeJson(entry.detailsJson());
        AuditLogEntity entity = new AuditLogEntity(
                entry.id(),
                entry.tenantId().value(),
                entry.branchId() != null ? entry.branchId().value() : null,
                entry.actorUserId(),
                entry.action().name(),
                entry.targetEntityType(),
                entry.targetEntityId(),
                sanitizedJson,
                entry.correlationId().value(),
                entry.occurredAt()
        );
        exceptionTranslator.execute("record audit entry", () -> repository.saveAndFlush(entity));
        AUDIT_LOG.info(
                "auditId={} tenantId={} branchId={} actorUserId={} action={} targetType={} targetId={} correlationId={}",
                entry.id(),
                entry.tenantId(),
                entry.branchId(),
                entry.actorUserId(),
                entry.action(),
                entry.targetEntityType(),
                entry.targetEntityId(),
                entry.correlationId()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEntry> findRecentByBranch(TenantId tenantId, BranchId branchId, int page, int size) {
        return repository.findByTenantIdAndBranchIdOrderByOccurredAtDesc(
                        tenantId.value(),
                        branchId.value(),
                        PageRequest.of(page, size)
                )
                .stream()
                .map(entity -> new AuditEntry(
                        entity.getId(),
                        TenantId.of(entity.getTenantId()),
                        entity.getBranchId() != null ? BranchId.of(entity.getBranchId()) : null,
                        entity.getActorUserId(),
                        AuditAction.valueOf(entity.getAction()),
                        entity.getTargetEntityType(),
                        entity.getTargetEntityId(),
                        entity.getDetailsJson(),
                        CorrelationId.of(entity.getCorrelationId()),
                        entity.getOccurredAt()
                ))
                .toList();
    }
}
