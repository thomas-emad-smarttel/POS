package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, UUID> {
    Optional<PermissionEntity> findByCode(String code);
    List<PermissionEntity> findAllByOrderByModuleAscCodeAsc();
}
