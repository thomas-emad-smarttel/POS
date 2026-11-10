package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.port.TenantRepositoryPort;
import com.restaurantpos.domain.tenancy.Tenant;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.TenantEntity;
import com.restaurantpos.infrastructure.persistence.repository.TenantJpaRepository;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class TenantJpaAdapter implements TenantRepositoryPort {

    private final TenantJpaRepository repository;
    private final DatabaseExceptionTranslator translator;

    public TenantJpaAdapter(TenantJpaRepository repository, DatabaseExceptionTranslator translator) {
        this.repository = repository;
        this.translator = translator;
    }

    @Override
    @Transactional
    public Tenant save(Tenant tenant) {
        TenantEntity entity = repository.findById(tenant.id())
                .map(existing -> {
                    existing.setDisplayName(tenant.displayName());
                    existing.setStatus(tenant.status());
                    return existing;
                })
                .orElseGet(() -> new TenantEntity(
                        tenant.id(),
                        tenant.code(),
                        tenant.legalName(),
                        tenant.displayName(),
                        tenant.defaultCurrency(),
                        tenant.status()
                ));
        TenantEntity saved = translator.execute("save tenant", () -> repository.saveAndFlush(entity));
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tenant> findById(TenantId tenantId) {
        return repository.findById(tenantId.value()).map(TenantJpaAdapter::toDomain);
    }

    private static Tenant toDomain(TenantEntity entity) {
        return new Tenant(
                entity.getId(),
                entity.getCode(),
                entity.getLegalName(),
                entity.getDisplayName(),
                entity.getDefaultCurrency(),
                entity.getStatus(),
                entity.getVersion()
        );
    }
}
