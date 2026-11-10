package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.port.TenantQueryPort;
import com.restaurantpos.domain.tenancy.Tenant;
import com.restaurantpos.infrastructure.persistence.repository.TenantJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class TenantQueryJpaAdapter implements TenantQueryPort {

    private final TenantJpaRepository repository;

    public TenantQueryJpaAdapter(TenantJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tenant> findByCode(String code) {
        return repository.findByCode(code)
                .map(e -> new Tenant(e.getId(), e.getCode(), e.getLegalName(), e.getDisplayName(), e.getDefaultCurrency(), e.getStatus(), e.getVersion()));
    }
}
