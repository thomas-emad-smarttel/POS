package com.restaurantpos.domain.port;

import com.restaurantpos.domain.security.SupervisorApproval;
import com.restaurantpos.shared.model.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound persistence port for immutable supervisor approval/rejection records.
 */
public interface SupervisorApprovalRepositoryPort {
    SupervisorApproval save(SupervisorApproval approval);

    Optional<SupervisorApproval> findByIdScoped(UUID approvalId, TenantId tenantId);
}
