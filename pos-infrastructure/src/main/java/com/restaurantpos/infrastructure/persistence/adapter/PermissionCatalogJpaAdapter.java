package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.port.PermissionCatalogPort;
import com.restaurantpos.domain.security.PermissionDefinition;
import com.restaurantpos.infrastructure.persistence.entity.PermissionEntity;
import com.restaurantpos.infrastructure.persistence.repository.PermissionJpaRepository;
import com.restaurantpos.shared.security.PermissionCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class PermissionCatalogJpaAdapter implements PermissionCatalogPort {

    private final PermissionJpaRepository repository;

    public PermissionCatalogJpaAdapter(PermissionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void ensureCatalogSynced() {
        Map<String, PermissionEntity> existingByCode = repository.findAll().stream()
                .collect(Collectors.toMap(PermissionEntity::getCode, Function.identity()));

        for (PermissionCode code : PermissionCode.values()) {
            PermissionEntity existing = existingByCode.get(code.code());
            if (existing == null) {
                repository.save(new PermissionEntity(
                        UUID.randomUUID(),
                        code.code(),
                        code.module().name(),
                        code.description(),
                        code.sensitive()
                ));
            } else if (!existing.getModule().equals(code.module().name())
                    || !existing.getDescription().equals(code.description())
                    || existing.isSensitive() != code.sensitive()) {
                existing.setModule(code.module().name());
                existing.setDescription(code.description());
                existing.setSensitive(code.sensitive());
                repository.save(existing);
            }
        }
        repository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionDefinition> findAll() {
        return repository.findAllByOrderByModuleAscCodeAsc().stream()
                .flatMap(e -> PermissionCode.fromCode(e.getCode())
                        .map(code -> new PermissionDefinition(e.getId(), code, e.getModule(), e.getDescription(), e.isSensitive()))
                        .stream())
                .toList();
    }
}
