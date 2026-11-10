package com.restaurantpos.domain.port;

import com.restaurantpos.domain.cash.CashMovement;
import com.restaurantpos.domain.cash.CashSession;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashSessionRepositoryPort {

    CashSession saveSession(CashSession session);

    Optional<CashSession> findSessionScoped(UUID sessionId, TenantId tenantId, BranchId branchId);

    Optional<CashSession> findActiveByTerminalScoped(TenantId tenantId, BranchId branchId, UUID terminalId);

    List<CashSession> findSessionsByBranch(TenantId tenantId, BranchId branchId, int page, int size);

    List<CashSession> findVarianceSessions(TenantId tenantId, BranchId branchId);

    CashMovement appendMovement(CashMovement movement);

    List<CashMovement> findMovementsBySession(UUID cashSessionId, TenantId tenantId);

    Optional<CashMovement> findMovementByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey);
}
