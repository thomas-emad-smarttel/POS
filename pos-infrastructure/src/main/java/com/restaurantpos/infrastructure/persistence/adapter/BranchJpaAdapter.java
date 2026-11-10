package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.port.BranchRepositoryPort;
import com.restaurantpos.domain.tenancy.Branch;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.BranchEntity;
import com.restaurantpos.infrastructure.persistence.repository.BranchJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class BranchJpaAdapter implements BranchRepositoryPort {

    private final BranchJpaRepository repository;
    private final DatabaseExceptionTranslator translator;

    public BranchJpaAdapter(BranchJpaRepository repository, DatabaseExceptionTranslator translator) {
        this.repository = repository;
        this.translator = translator;
    }

    @Override
    @Transactional
    public Branch save(Branch branch) {
        BranchEntity entity = repository.findByIdAndTenantId(branch.id(), branch.tenantId().value())
                .map(existing -> {
                    existing.setName(branch.name());
                    existing.setActive(branch.active());
                    return existing;
                })
                .orElseGet(() -> new BranchEntity(
                        branch.id(),
                        branch.tenantId().value(),
                        branch.code(),
                        branch.name(),
                        branch.timezone(),
                        branch.currencyCode(),
                        branch.active()
                ));
        BranchEntity saved = translator.execute("save branch", () -> repository.saveAndFlush(entity));
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Branch> findByIdAndTenant(BranchId branchId, TenantId tenantId) {
        return repository.findByIdAndTenantId(branchId.value(), tenantId.value()).map(BranchJpaAdapter::toDomain);
    }

    private static Branch toDomain(BranchEntity entity) {
        return new Branch(
                entity.getId(),
                TenantId.of(entity.getTenantId()),
                entity.getCode(),
                entity.getName(),
                entity.getTimezone(),
                entity.getCurrencyCode(),
                entity.isActive(),
                entity.getVersion()
        );
    }
}
