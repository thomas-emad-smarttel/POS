package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.UserSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionJpaRepository extends JpaRepository<UserSessionEntity, UUID> {
    Optional<UserSessionEntity> findByTokenHash(String tokenHash);
    Optional<UserSessionEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<UserSessionEntity> findByTenantIdAndUserIdAndRevokedAtIsNull(UUID tenantId, UUID userId);
    Page<UserSessionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
