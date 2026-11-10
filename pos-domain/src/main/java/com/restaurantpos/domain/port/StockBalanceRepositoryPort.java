package com.restaurantpos.domain.port;

import com.restaurantpos.domain.inventory.StockBalanceView;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound persistence port for optimistically locked stock balance projections.
 */
public interface StockBalanceRepositoryPort {
    StockBalanceView save(StockBalanceView balance);

    Optional<StockBalanceView> findScoped(TenantId tenantId, BranchId branchId, UUID stockLocationId, UUID ingredientId);
}
