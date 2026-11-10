package com.restaurantpos.domain.port;

import com.restaurantpos.domain.table.DiningArea;
import com.restaurantpos.domain.table.DiningTable;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TableRepositoryPort {
    DiningArea saveArea(DiningArea area);
    Optional<DiningArea> findAreaScoped(UUID areaId, TenantId tenantId, BranchId branchId);
    List<DiningArea> findAreasByBranch(TenantId tenantId, BranchId branchId);

    DiningTable saveTable(DiningTable table);
    Optional<DiningTable> findTableScoped(UUID tableId, TenantId tenantId, BranchId branchId);
    List<DiningTable> findTablesByBranch(TenantId tenantId, BranchId branchId);
}
