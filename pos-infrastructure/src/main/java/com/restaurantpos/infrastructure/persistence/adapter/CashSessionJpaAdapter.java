package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.cash.CashMovement;
import com.restaurantpos.domain.cash.CashMovementType;
import com.restaurantpos.domain.cash.CashSession;
import com.restaurantpos.domain.cash.CashSessionStatus;
import com.restaurantpos.domain.port.CashSessionRepositoryPort;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.CashMovementEntity;
import com.restaurantpos.infrastructure.persistence.entity.CashSessionEntity;
import com.restaurantpos.infrastructure.persistence.repository.CashMovementJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.CashSessionJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class CashSessionJpaAdapter implements CashSessionRepositoryPort {

    private final CashSessionJpaRepository sessionRepo;
    private final CashMovementJpaRepository movementRepo;
    private final DatabaseExceptionTranslator translator;

    public CashSessionJpaAdapter(
            CashSessionJpaRepository sessionRepo,
            CashMovementJpaRepository movementRepo,
            DatabaseExceptionTranslator translator
    ) {
        this.sessionRepo = sessionRepo;
        this.movementRepo = movementRepo;
        this.translator = translator;
    }

    @Override
    @Transactional
    public CashSession saveSession(CashSession session) {
        CashSessionEntity entity = sessionRepo.findByIdAndTenantIdAndBranchId(
                        session.id(), session.tenantId().value(), session.branchId().value())
                .map(existing -> {
                    existing.setClosedByUserId(session.closedByUserId());
                    existing.setReopenedByUserId(session.reopenedByUserId());
                    existing.setExpectedCashAmount(session.expectedCashAmount());
                    existing.setCountedCashAmount(session.countedCashAmount());
                    existing.setVarianceAmount(session.varianceAmount());
                    existing.setStatus(session.status().name());
                    existing.setClosedAt(session.closedAt());
                    existing.setReopenedAt(session.reopenedAt());
                    existing.setReopenReason(session.reopenReason());
                    return existing;
                })
                .orElseGet(() -> {
                    CashSessionEntity created = new CashSessionEntity(
                            session.id(), session.tenantId().value(), session.branchId().value(),
                            session.terminalId(), session.openedByUserId(), session.currencyCode(),
                            session.openingFloat(), session.expectedCashAmount(),
                            session.status().name(), session.openedAt()
                    );
                    created.setClosedByUserId(session.closedByUserId());
                    created.setReopenedByUserId(session.reopenedByUserId());
                    created.setCountedCashAmount(session.countedCashAmount());
                    created.setVarianceAmount(session.varianceAmount());
                    created.setClosedAt(session.closedAt());
                    created.setReopenedAt(session.reopenedAt());
                    created.setReopenReason(session.reopenReason());
                    return created;
                });
        CashSessionEntity saved = translator.execute("save cash session", () -> sessionRepo.saveAndFlush(entity));
        return toSessionDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashSession> findSessionScoped(UUID sessionId, TenantId tenantId, BranchId branchId) {
        return sessionRepo.findByIdAndTenantIdAndBranchId(sessionId, tenantId.value(), branchId.value())
                .map(CashSessionJpaAdapter::toSessionDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashSession> findActiveByTerminalScoped(TenantId tenantId, BranchId branchId, UUID terminalId) {
        return sessionRepo.findFirstByTenantIdAndBranchIdAndTerminalIdAndStatusIn(
                        tenantId.value(), branchId.value(), terminalId, List.of("OPEN", "REOPENED"))
                .map(CashSessionJpaAdapter::toSessionDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashSession> findSessionsByBranch(TenantId tenantId, BranchId branchId, int page, int size) {
        return sessionRepo.findByTenantIdAndBranchIdOrderByOpenedAtDesc(
                        tenantId.value(), branchId.value(), PageRequest.of(page, size))
                .stream()
                .map(CashSessionJpaAdapter::toSessionDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashSession> findVarianceSessions(TenantId tenantId, BranchId branchId) {
        return sessionRepo.findSessionsWithVariance(tenantId.value(), branchId.value()).stream()
                .map(CashSessionJpaAdapter::toSessionDomain)
                .toList();
    }

    @Override
    @Transactional
    public CashMovement appendMovement(CashMovement movement) {
        CashMovementEntity saved = translator.execute("append cash movement", () -> movementRepo.saveAndFlush(
                new CashMovementEntity(
                        movement.id(), movement.tenantId().value(), movement.branchId().value(),
                        movement.cashSessionId(), movement.actorUserId(), movement.movementType().name(),
                        movement.amount(), movement.reason(), movement.correlationId(),
                        movement.idempotencyKey(), movement.occurredAt()
                )));
        return toMovementDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashMovement> findMovementsBySession(UUID cashSessionId, TenantId tenantId) {
        return movementRepo.findByCashSessionIdAndTenantIdOrderByOccurredAtAsc(cashSessionId, tenantId.value())
                .stream()
                .map(CashSessionJpaAdapter::toMovementDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashMovement> findMovementByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey) {
        return movementRepo.findByTenantIdAndBranchIdAndIdempotencyKey(tenantId.value(), branchId.value(), idempotencyKey)
                .map(CashSessionJpaAdapter::toMovementDomain);
    }

    private static CashSession toSessionDomain(CashSessionEntity e) {
        return new CashSession(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getTerminalId(), e.getOpenedByUserId(), e.getClosedByUserId(), e.getReopenedByUserId(),
                e.getCurrencyCode(), e.getOpeningFloat(), e.getExpectedCashAmount(),
                e.getCountedCashAmount(), e.getVarianceAmount(), CashSessionStatus.valueOf(e.getStatus()),
                e.getOpenedAt(), e.getClosedAt(), e.getReopenedAt(), e.getReopenReason(), e.getVersion()
        );
    }

    private static CashMovement toMovementDomain(CashMovementEntity e) {
        return new CashMovement(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getCashSessionId(), e.getActorUserId(), CashMovementType.valueOf(e.getMovementType()),
                e.getAmount(), e.getReason(), e.getCorrelationId(), e.getIdempotencyKey(), e.getOccurredAt()
        );
    }
}
