package com.restaurantpos.domain.port;

import com.restaurantpos.domain.menu.ProductCatalogView;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.List;

/**
 * Read-optimized query port returning paginated branch menu catalog projections.
 */
public interface ProductQueryPort {
    List<ProductCatalogView> findBranchCatalog(TenantId tenantId, BranchId branchId, int page, int size);
}
