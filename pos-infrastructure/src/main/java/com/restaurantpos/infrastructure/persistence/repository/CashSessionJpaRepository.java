package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.CashSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashSessionJpaRepository extends JpaRepository<CashSessionEntity, UUID> {

    Optional<CashSessionEntity> findByIdAndTenantIdAndBranchId(UUID id, UUID tenantId, UUID branchId);

    Optional<CashSessionEntity> findFirstByTenantIdAndBranchIdAndTerminalIdAndStatusIn(
            UUID tenantId, UUID branchId, UUID terminalId, Collection<String> statuses);

    Page<CashSessionEntity> findByTenantIdAndBranchIdOrderByOpenedAtDesc(UUID tenantId, UUID branchId, Pageable pageable);

    @Query("""
            SELECT s
            FROM CashSessionEntity s
            WHERE s.tenantId = :tenantId
              AND s.branchId = :branchId
              AND s.varianceAmount IS NOT NULL
              AND s.varianceAmount <> 0
            ORDER BY s.closedAt DESC
            """)
    List<CashSessionEntity> findSessionsWithVariance(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId
    );
}
