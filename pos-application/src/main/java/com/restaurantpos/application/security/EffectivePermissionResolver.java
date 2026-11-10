package com.restaurantpos.application.security;

import com.restaurantpos.domain.port.RoleRepositoryPort;
import com.restaurantpos.domain.port.UserRepositoryPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.security.RoleDefinition;
import com.restaurantpos.domain.security.UserAccount;
import com.restaurantpos.domain.security.UserRoleAssignment;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.security.PermissionCode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Evaluates a user's live effective roles and permissions for a specific branch scope.
 */
public class EffectivePermissionResolver {

    private final UserRepositoryPort userRepository;
    private final RoleRepositoryPort roleRepository;

    public EffectivePermissionResolver(UserRepositoryPort userRepository, RoleRepositoryPort roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public AccessPrincipal resolve(
            UserAccount user,
            BranchId activeBranchId,
            UUID terminalId,
            UUID sessionId
    ) {
        Set<BranchId> allowedBranches = userRepository.findAllowedBranches(user.tenantId(), user.id());
        List<UserRoleAssignment> assignments = userRepository.findRoleAssignments(user.tenantId(), user.id());

        boolean branchAuthorized = activeBranchId != null && allowedBranches.contains(activeBranchId);
        Set<String> assignedRoles = new HashSet<>();
        Set<PermissionCode> effectivePermissions = new HashSet<>();

        Map<UUID, RoleDefinition> roleCache = new HashMap<>();
        for (UserRoleAssignment assignment : assignments) {
            boolean appliesToBranch = assignment.branchId() == null
                    || (branchAuthorized && assignment.branchId().equals(activeBranchId.value()));
            if (!appliesToBranch) {
                continue;
            }
            RoleDefinition role = roleCache.computeIfAbsent(
                    assignment.roleId(),
                    id -> roleRepository.findByIdAndTenant(id, user.tenantId()).orElse(null)
            );
            if (role != null && role.active()) {
                assignedRoles.add(role.code());
                if (branchAuthorized || "TENANT".equals(role.scopeType())) {
                    effectivePermissions.addAll(role.permissions());
                }
            }
        }

        return new AccessPrincipal(
                user.id(),
                user.username(),
                user.tenantId(),
                activeBranchId,
                terminalId,
                sessionId,
                allowedBranches,
                assignedRoles,
                effectivePermissions,
                user.active()
        );
    }
}
