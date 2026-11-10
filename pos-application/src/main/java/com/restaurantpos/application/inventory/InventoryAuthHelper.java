package com.restaurantpos.application.inventory;

import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.security.PermissionCode;

final class InventoryAuthHelper {

    private InventoryAuthHelper() {
    }

    static BranchId requireActiveBranchPermission(AccessPrincipal actor, PermissionCode primary, PermissionCode fallback) {
        PermissionCode needed = (fallback == null || (actor != null && actor.hasPermission(primary))) ? primary : fallback;
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(needed, "Active branch session is required");
        }
        BranchId branchId = actor.activeBranchId();
        PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), branchId, needed);
        return branchId;
    }
}
