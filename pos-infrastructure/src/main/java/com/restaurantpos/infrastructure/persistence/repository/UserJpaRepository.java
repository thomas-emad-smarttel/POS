package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByTenantIdAndUsername(UUID tenantId, String username);
    Optional<UserEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Page<UserEntity> findByTenantIdOrderByUsernameAsc(UUID tenantId, Pageable pageable);
}
