package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.port.SupervisorApprovalRepositoryPort;
import com.restaurantpos.domain.security.SupervisorApproval;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.SupervisorApprovalEntity;
import com.restaurantpos.infrastructure.persistence.repository.SupervisorApprovalJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.PermissionCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class SupervisorApprovalJpaAdapter implements SupervisorApprovalRepositoryPort {

    private final SupervisorApprovalJpaRepository repository;
    private final DatabaseExceptionTranslator translator;

    public SupervisorApprovalJpaAdapter(
            SupervisorApprovalJpaRepository repository,
            DatabaseExceptionTranslator translator
    ) {
        this.repository = repository;
        this.translator = translator;
    }

    @Override
    @Transactional
    public SupervisorApproval save(SupervisorApproval approval) {
        SupervisorApprovalEntity entity = new SupervisorApprovalEntity(
                approval.id(),
                approval.tenantId().value(),
                approval.branchId().value(),
                approval.terminalId(),
                approval.requiredPermission().code(),
                approval.targetEntityType(),
                approval.targetEntityId(),
                approval.requestedByUserId(),
                approval.approvedByUserId(),
                approval.status(),
                approval.reason(),
                approval.correlationId().value(),
                approval.expiresAt(),
                approval.createdAt()
        );
        translator.execute("save supervisor approval", () -> repository.saveAndFlush(entity));
        return approval;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupervisorApproval> findByIdScoped(UUID approvalId, TenantId tenantId) {
        return repository.findByIdAndTenantId(approvalId, tenantId.value())
                .map(e -> new SupervisorApproval(
                        e.getId(),
                        TenantId.of(e.getTenantId()),
                        BranchId.of(e.getBranchId()),
                        e.getTerminalId(),
                        PermissionCode.fromCode(e.getRequiredPermission()).orElse(PermissionCode.PAYMENTS_REFUND),
                        e.getTargetEntityType(),
                        e.getTargetEntityId(),
                        e.getRequestedByUserId(),
                        e.getApprovedByUserId(),
                        e.getStatus(),
                        e.getReason(),
                        CorrelationId.of(e.getCorrelationId()),
                        e.getExpiresAt(),
                        e.getCreatedAt()
                ));
    }
}
