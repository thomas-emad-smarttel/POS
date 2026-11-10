package com.restaurantpos.domain.port;

import com.restaurantpos.domain.security.UserAccount;
import com.restaurantpos.domain.security.UserRoleAssignment;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound persistence port for tenant-isolated user accounts, branch access, and role assignments.
 */
public interface UserRepositoryPort {
    UserAccount save(UserAccount user);

    Optional<UserAccount> findByIdAndTenant(UUID userId, TenantId tenantId);

    Optional<UserAccount> findByTenantAndUsername(TenantId tenantId, String username);

    List<UserAccount> findByTenant(TenantId tenantId, int page, int size);

    Set<BranchId> findAllowedBranches(TenantId tenantId, UUID userId);

    void replaceBranchAccess(TenantId tenantId, UUID userId, Set<BranchId> branchIds);

    List<UserRoleAssignment> findRoleAssignments(TenantId tenantId, UUID userId);

    void replaceRoleAssignments(TenantId tenantId, UUID userId, List<UserRoleAssignment> assignments);
}
