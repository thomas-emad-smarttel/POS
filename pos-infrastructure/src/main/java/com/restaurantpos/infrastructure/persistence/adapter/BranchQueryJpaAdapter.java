package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.port.BranchQueryPort;
import com.restaurantpos.domain.tenancy.Branch;
import com.restaurantpos.infrastructure.persistence.repository.BranchJpaRepository;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class BranchQueryJpaAdapter implements BranchQueryPort {

    private final BranchJpaRepository repository;

    public BranchQueryJpaAdapter(BranchJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Branch> findByTenant(TenantId tenantId) {
        return repository.findByTenantIdOrderByNameAsc(tenantId.value()).stream()
                .map(e -> new Branch(
                        e.getId(),
                        TenantId.of(e.getTenantId()),
                        e.getCode(),
                        e.getName(),
                        e.getTimezone(),
                        e.getCurrencyCode(),
                        e.isActive(),
                        e.getVersion()
                ))
                .toList();
    }
}
