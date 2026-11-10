package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.port.UserSessionRepositoryPort;
import com.restaurantpos.domain.security.UserSession;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.UserSessionEntity;
import com.restaurantpos.infrastructure.persistence.repository.UserSessionJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class UserSessionJpaAdapter implements UserSessionRepositoryPort {

    private final UserSessionJpaRepository repository;
    private final DatabaseExceptionTranslator translator;

    public UserSessionJpaAdapter(UserSessionJpaRepository repository, DatabaseExceptionTranslator translator) {
        this.repository = repository;
        this.translator = translator;
    }

    @Override
    @Transactional
    public UserSession create(UserSession session) {
        UserSessionEntity entity = new UserSessionEntity(
                session.id(),
                session.tenantId().value(),
                session.branchId() != null ? session.branchId().value() : null,
                session.userId(),
                session.terminalId(),
                session.tokenHash(),
                session.expiresAt(),
                session.revokedAt(),
                session.createdAt()
        );
        return toDomain(translator.execute("create user session", () -> repository.saveAndFlush(entity)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSession> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(UserSessionJpaAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSession> findByIdAndTenant(UUID sessionId, TenantId tenantId) {
        return repository.findByIdAndTenantId(sessionId, tenantId.value()).map(UserSessionJpaAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSession> findActiveByUser(TenantId tenantId, UUID userId) {
        return repository.findByTenantIdAndUserIdAndRevokedAtIsNull(tenantId.value(), userId).stream()
                .map(UserSessionJpaAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSession> findByTenant(TenantId tenantId, int page, int size) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId.value(), PageRequest.of(page, size))
                .stream()
                .map(UserSessionJpaAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void updateActiveBranch(UUID sessionId, TenantId tenantId, BranchId branchId) {
        repository.findByIdAndTenantId(sessionId, tenantId.value()).ifPresent(entity -> {
            entity.setBranchId(branchId.value());
            repository.saveAndFlush(entity);
        });
    }

    @Override
    @Transactional
    public void revokeSession(UUID sessionId, TenantId tenantId, Instant revokedAt) {
        repository.findByIdAndTenantId(sessionId, tenantId.value()).ifPresent(entity -> {
            if (entity.getRevokedAt() == null) {
                entity.setRevokedAt(revokedAt);
                repository.saveAndFlush(entity);
            }
        });
    }

    @Override
    @Transactional
    public void revokeAllForUser(TenantId tenantId, UUID userId, Instant revokedAt) {
        List<UserSessionEntity> active = repository.findByTenantIdAndUserIdAndRevokedAtIsNull(tenantId.value(), userId);
        for (UserSessionEntity entity : active) {
            entity.setRevokedAt(revokedAt);
        }
        repository.saveAllAndFlush(active);
    }

    private static UserSession toDomain(UserSessionEntity entity) {
        return new UserSession(
                entity.getId(),
                TenantId.of(entity.getTenantId()),
                entity.getBranchId() != null ? BranchId.of(entity.getBranchId()) : null,
                entity.getUserId(),
                entity.getTerminalId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getCreatedAt()
        );
    }
}
