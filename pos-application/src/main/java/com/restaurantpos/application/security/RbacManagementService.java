package com.restaurantpos.application.security;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.port.AuditLogQueryPort;
import com.restaurantpos.domain.port.BranchQueryPort;
import com.restaurantpos.domain.port.BranchRepositoryPort;
import com.restaurantpos.domain.port.PasswordHasherPort;
import com.restaurantpos.domain.port.PermissionCatalogPort;
import com.restaurantpos.domain.port.RoleRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.port.UserRepositoryPort;
import com.restaurantpos.domain.port.UserSessionRepositoryPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.security.AuditEntry;
import com.restaurantpos.domain.security.RoleDefinition;
import com.restaurantpos.domain.security.UserAccount;
import com.restaurantpos.domain.security.UserRoleAssignment;
import com.restaurantpos.domain.security.UserSession;
import com.restaurantpos.shared.dto.security.AssignUserBranchesRequest;
import com.restaurantpos.shared.dto.security.AssignUserRolesRequest;
import com.restaurantpos.shared.dto.security.AuditLogDto;
import com.restaurantpos.shared.dto.security.BranchSummaryDto;
import com.restaurantpos.shared.dto.security.CreateRoleRequest;
import com.restaurantpos.shared.dto.security.CreateUserRequest;
import com.restaurantpos.shared.dto.security.PermissionDto;
import com.restaurantpos.shared.dto.security.RoleDto;
import com.restaurantpos.shared.dto.security.SessionDto;
import com.restaurantpos.shared.dto.security.UpdateRolePermissionsRequest;
import com.restaurantpos.shared.dto.security.UpdateRoleRequest;
import com.restaurantpos.shared.dto.security.UpdateUserRequest;
import com.restaurantpos.shared.dto.security.UserRoleAssignmentDto;
import com.restaurantpos.shared.dto.security.UserSummaryDto;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service governing tenant-isolated user, role, permission, branch-access, session, and audit operations.
 */
public class RbacManagementService {

    private static final Set<PermissionCode> PROTECTED_ADMIN_PERMISSIONS = Set.of(
            PermissionCode.ROLES_VIEW,
            PermissionCode.ROLES_MANAGE,
            PermissionCode.ROLES_ASSIGN,
            PermissionCode.USERS_VIEW,
            PermissionCode.USERS_MANAGE,
            PermissionCode.SETTINGS_MANAGE
    );

    private final UserRepositoryPort userRepository;
    private final RoleRepositoryPort roleRepository;
    private final PermissionCatalogPort permissionCatalog;
    private final BranchRepositoryPort branchRepository;
    private final BranchQueryPort branchQuery;
    private final UserSessionRepositoryPort sessionRepository;
    private final AuditLogQueryPort auditLogQuery;
    private final PasswordHasherPort passwordHasher;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;

    public RbacManagementService(
            UserRepositoryPort userRepository,
            RoleRepositoryPort roleRepository,
            PermissionCatalogPort permissionCatalog,
            BranchRepositoryPort branchRepository,
            BranchQueryPort branchQuery,
            UserSessionRepositoryPort sessionRepository,
            AuditLogQueryPort auditLogQuery,
            PasswordHasherPort passwordHasher,
            AuditRecordingService auditService,
            SystemClockPort clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.permissionCatalog = Objects.requireNonNull(permissionCatalog);
        this.branchRepository = Objects.requireNonNull(branchRepository);
        this.branchQuery = Objects.requireNonNull(branchQuery);
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.auditLogQuery = Objects.requireNonNull(auditLogQuery);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.auditService = Objects.requireNonNull(auditService);
        this.clock = Objects.requireNonNull(clock);
    }

    public List<UserSummaryDto> listUsers(AccessPrincipal actor, int page, int size) {
        requirePermission(actor, PermissionCode.USERS_VIEW);
        return userRepository.findByTenant(actor.tenantId(), page, size).stream()
                .map(user -> toUserSummary(actor.tenantId(), user))
                .toList();
    }

    public UserSummaryDto createUser(AccessPrincipal actor, CreateUserRequest request, CorrelationId correlationId) {
        requireAnyPermission(actor, PermissionCode.USERS_MANAGE, PermissionCode.USERS_CREATE);
        Set<BranchId> branchIds = validateTenantBranches(actor.tenantId(), request.branchIds());

        UUID userId = UUID.randomUUID();
        UserAccount created = userRepository.save(new UserAccount(
                userId,
                actor.tenantId(),
                request.username().trim(),
                request.fullName().trim(),
                request.email() != null ? request.email().trim() : null,
                passwordHasher.hash(request.password()),
                true,
                0,
                null,
                null,
                0L
        ));
        if (!branchIds.isEmpty()) {
            userRepository.replaceBranchAccess(actor.tenantId(), userId, branchIds);
        }
        auditService.record(
                actor.tenantId(),
                actor.activeBranchId(),
                actor.userId(),
                AuditAction.USER_CREATED,
                "User",
                userId.toString(),
                "{\"username\":\"" + created.username() + "\"}",
                correlationId
        );
        return toUserSummary(actor.tenantId(), created);
    }

    public UserSummaryDto updateUser(AccessPrincipal actor, UUID userId, UpdateUserRequest request, CorrelationId correlationId) {
        requireAnyPermission(actor, PermissionCode.USERS_MANAGE, PermissionCode.USERS_UPDATE);
        UserAccount existing = userRepository.findByIdAndTenant(userId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserAccount saved = userRepository.save(new UserAccount(
                existing.id(),
                existing.tenantId(),
                existing.username(),
                request.fullName().trim(),
                request.email() != null ? request.email().trim() : null,
                existing.passwordHash(),
                existing.active(),
                existing.failedLoginCount(),
                existing.lockedUntil(),
                existing.lastLoginAt(),
                existing.version()
        ));
        auditService.record(
                actor.tenantId(),
                actor.activeBranchId(),
                actor.userId(),
                AuditAction.USER_UPDATED,
                "User",
                userId.toString(),
                null,
                correlationId
        );
        return toUserSummary(actor.tenantId(), saved);
    }

    public UserSummaryDto setUserActive(AccessPrincipal actor, UUID userId, boolean active, CorrelationId correlationId) {
        requireAnyPermission(actor, PermissionCode.USERS_MANAGE, PermissionCode.USERS_DISABLE);
        if (!active && actor.userId().equals(userId)) {
            throw new DomainException("SELF_DEACTIVATION_BLOCKED", "You cannot deactivate your own active account");
        }
        UserAccount existing = userRepository.findByIdAndTenant(userId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserAccount saved = userRepository.save(new UserAccount(
                existing.id(),
                existing.tenantId(),
                existing.username(),
                existing.fullName(),
                existing.email(),
                existing.passwordHash(),
                active,
                active ? 0 : existing.failedLoginCount(),
                active ? null : existing.lockedUntil(),
                existing.lastLoginAt(),
                existing.version()
        ));

        if (!active) {
            sessionRepository.revokeAllForUser(actor.tenantId(), userId, clock.now());
        }

        auditService.record(
                actor.tenantId(),
                actor.activeBranchId(),
                actor.userId(),
                active ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED,
                "User",
                userId.toString(),
                "{\"active\":" + active + "}",
                correlationId
        );
        return toUserSummary(actor.tenantId(), saved);
    }

    public UserSummaryDto assignBranches(
            AccessPrincipal actor,
            UUID userId,
            AssignUserBranchesRequest request,
            CorrelationId correlationId
    ) {
        requireAnyPermission(actor, PermissionCode.USERS_MANAGE, PermissionCode.USERS_UPDATE);
        UserAccount user = userRepository.findByIdAndTenant(userId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Set<BranchId> branchIds = validateTenantBranches(actor.tenantId(), request.branchIds());
        userRepository.replaceBranchAccess(actor.tenantId(), userId, branchIds);

        auditService.record(
                actor.tenantId(),
                actor.activeBranchId(),
                actor.userId(),
                AuditAction.BRANCH_ACCESS_CHANGED,
                "User",
                userId.toString(),
                "{\"branchCount\":" + branchIds.size() + "}",
                correlationId
        );
        return toUserSummary(actor.tenantId(), user);
    }

    public UserSummaryDto assignUserRoles(
            AccessPrincipal actor,
            UUID userId,
            AssignUserRolesRequest request,
            CorrelationId correlationId
    ) {
        requirePermission(actor, PermissionCode.ROLES_ASSIGN);
        UserAccount user = userRepository.findByIdAndTenant(userId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Set<String> dedupKeys = new HashSet<>();
        List<UserRoleAssignment> resolved = new java.util.ArrayList<>();
        for (AssignUserRolesRequest.RoleScopeEntry entry : request.assignments()) {
            RoleDefinition role = roleRepository.findByIdAndTenant(entry.roleId(), actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found in tenant"));
            if (!role.active()) {
                throw new DomainException("ROLE_INACTIVE", "Cannot assign inactive role: " + role.code());
            }
            if (entry.branchId() != null) {
                branchRepository.findByIdAndTenant(BranchId.of(entry.branchId()), actor.tenantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Branch not found in tenant"));
            }
            enforceNoPrivilegeEscalation(actor, role);

            String dedupKey = role.id() + ":" + (entry.branchId() != null ? entry.branchId() : "TENANT");
            if (!dedupKeys.add(dedupKey)) {
                throw new DomainException("DUPLICATE_ASSIGNMENT", "Duplicate role assignment for role " + role.code());
            }
            resolved.add(new UserRoleAssignment(
                    UUID.randomUUID(),
                    actor.tenantId(),
                    userId,
                    role.id(),
                    role.code(),
                    role.name(),
                    entry.branchId()
            ));
        }

        userRepository.replaceRoleAssignments(actor.tenantId(), userId, resolved);

        auditService.record(
                actor.tenantId(),
                actor.activeBranchId(),
                actor.userId(),
                AuditAction.ROLE_ASSIGNED,
                "User",
                userId.toString(),
                "{\"assignmentCount\":" + resolved.size() + "}",
                correlationId
        );
        return toUserSummary(actor.tenantId(), user);
    }

    public List<RoleDto> listRoles(AccessPrincipal actor, int page, int size) {
        requirePermission(actor, PermissionCode.ROLES_VIEW);
        return roleRepository.findByTenant(actor.tenantId(), page, size).stream()
                .map(RbacManagementService::toRoleDto)
                .toList();
    }

    public RoleDto createRole(AccessPrincipal actor, CreateRoleRequest request, CorrelationId correlationId) {
        requireAnyPermission(actor, PermissionCode.ROLES_MANAGE, PermissionCode.ROLES_CREATE);
        Set<PermissionCode> permissions = parsePermissionCodes(request.permissionCodes());
        enforcePermissionSubset(actor, permissions);

        UUID roleId = UUID.randomUUID();
        RoleDefinition saved = roleRepository.save(new RoleDefinition(
                roleId,
                actor.tenantId(),
                request.code().trim(),
                request.name().trim(),
                request.description() != null ? request.description().trim() : null,
                request.scopeType(),
                false,
                true,
                request.maxDiscountPct(),
                permissions,
                0L
        ));
        roleRepository.replaceRolePermissions(actor.tenantId(), saved.id(), permissions);

        RoleDefinition reloaded = roleRepository.findByIdAndTenant(saved.id(), actor.tenantId()).orElse(saved);
        auditService.record(
                actor.tenantId(),
                actor.activeBranchId(),
                actor.userId(),
                AuditAction.ROLE_CREATED,
                "Role",
                saved.id().toString(),
                "{\"code\":\"" + saved.code() + "\"}",
                correlationId
        );
        return toRoleDto(reloaded);
    }

    public RoleDto updateRole(AccessPrincipal actor, UUID roleId, UpdateRoleRequest request, CorrelationId correlationId) {
        requireAnyPermission(actor, PermissionCode.ROLES_MANAGE, PermissionCode.ROLES_UPDATE);
        RoleDefinition existing = roleRepository.findByIdAndTenant(roleId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        if (existing.isProtectedSystemAdmin() && !request.active()) {
            throw new DomainException("PROTECTED_ROLE", "Protected system administrator role cannot be deactivated");
        }

        RoleDefinition saved = roleRepository.save(new RoleDefinition(
                existing.id(),
                existing.tenantId(),
                existing.code(),
                request.name().trim(),
                request.description() != null ? request.description().trim() : null,
                request.scopeType(),
                existing.systemTemplate(),
                request.active(),
                request.maxDiscountPct(),
                existing.permissions(),
                existing.version()
        ));

        auditService.record(
                actor.tenantId(),
                actor.activeBranchId(),
                actor.userId(),
                AuditAction.ROLE_UPDATED,
                "Role",
                roleId.toString(),
                "{\"active\":" + saved.active() + "}",
                correlationId
        );
        return toRoleDto(saved);
    }

    public RoleDto updateRolePermissions(
            AccessPrincipal actor,
            UUID roleId,
            UpdateRolePermissionsRequest request,
            CorrelationId correlationId
    ) {
        requireAnyPermission(actor, PermissionCode.ROLES_MANAGE, PermissionCode.ROLES_UPDATE);
        RoleDefinition existing = roleRepository.findByIdAndTenant(roleId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        Set<PermissionCode> requested = parsePermissionCodes(request.permissionCodes());
        enforcePermissionSubset(actor, requested);

        if (existing.isProtectedSystemAdmin() && !requested.containsAll(PROTECTED_ADMIN_PERMISSIONS)) {
            throw new DomainException(
                    "PROTECTED_PERMISSIONS",
                    "Cannot remove core administrative permissions from protected role " + existing.code()
            );
        }

        roleRepository.replaceRolePermissions(actor.tenantId(), roleId, requested);
        RoleDefinition updated = roleRepository.findByIdAndTenant(roleId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        auditService.record(
                actor.tenantId(),
                actor.activeBranchId(),
                actor.userId(),
                AuditAction.PERMISSION_CHANGED,
                "Role",
                roleId.toString(),
                "{\"permissionCount\":" + requested.size() + "}",
                correlationId
        );
        return toRoleDto(updated);
    }

    public List<PermissionDto> listPermissions(AccessPrincipal actor) {
        requirePermission(actor, PermissionCode.ROLES_VIEW);
        permissionCatalog.ensureCatalogSynced();
        return permissionCatalog.findAll().stream()
                .map(p -> new PermissionDto(p.id(), p.permission().code(), p.module(), p.description(), p.sensitive()))
                .toList();
    }

    public List<BranchSummaryDto> listBranches(AccessPrincipal actor) {
        requireAnyPermission(actor, PermissionCode.USERS_VIEW, PermissionCode.ROLES_VIEW);
        return branchQuery.findByTenant(actor.tenantId()).stream()
                .map(b -> new BranchSummaryDto(b.id(), b.code(), b.name(), b.timezone(), b.currencyCode(), b.active()))
                .toList();
    }

    public List<SessionDto> listSessions(AccessPrincipal actor, int page, int size) {
        requirePermission(actor, PermissionCode.AUDIT_VIEW);
        Map<UUID, String> usernames = new HashMap<>();
        return sessionRepository.findByTenant(actor.tenantId(), page, size).stream()
                .map(session -> {
                    String username = usernames.computeIfAbsent(
                            session.userId(),
                            uid -> userRepository.findByIdAndTenant(uid, actor.tenantId())
                                    .map(UserAccount::username)
                                    .orElse("unknown")
                    );
                    return new SessionDto(
                            session.id(),
                            session.userId(),
                            username,
                            session.branchId() != null ? session.branchId().value() : null,
                            session.terminalId(),
                            session.expiresAt(),
                            session.revokedAt(),
                            session.createdAt(),
                            session.id().equals(actor.sessionId())
                    );
                })
                .toList();
    }

    public void revokeSession(AccessPrincipal actor, UUID sessionId, CorrelationId correlationId) {
        requireAnyPermission(actor, PermissionCode.USERS_MANAGE, PermissionCode.AUDIT_VIEW);
        UserSession target = sessionRepository.findByIdAndTenant(sessionId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        sessionRepository.revokeSession(target.id(), actor.tenantId(), clock.now());

        auditService.record(
                actor.tenantId(),
                actor.activeBranchId(),
                actor.userId(),
                AuditAction.SESSION_REVOKED,
                "UserSession",
                sessionId.toString(),
                "{\"targetUserId\":\"" + target.userId() + "\"}",
                correlationId
        );
    }

    public List<AuditLogDto> listAuditLogs(AccessPrincipal actor, UUID branchId, int page, int size) {
        requirePermission(actor, PermissionCode.AUDIT_VIEW);
        BranchId targetBranch = branchId != null ? BranchId.of(branchId) : actor.activeBranchId();
        if (targetBranch == null || !actor.canAccessBranch(targetBranch)) {
            throw new UnauthorizedOperationException(PermissionCode.AUDIT_VIEW, "Not authorized to inspect audit logs for branch");
        }
        return auditLogQuery.findRecentByBranch(actor.tenantId(), targetBranch, page, size).stream()
                .map(RbacManagementService::toAuditDto)
                .toList();
    }

    private void requirePermission(AccessPrincipal actor, PermissionCode permission) {
        if (actor == null || !actor.active()) {
            throw new UnauthorizedOperationException(permission, "Active authenticated principal is required");
        }
        if (actor.activeBranchId() != null) {
            PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), permission);
        } else if (!actor.hasPermission(permission)) {
            throw new UnauthorizedOperationException(permission, "Missing required permission: " + permission.code());
        }
    }

    private void requireAnyPermission(AccessPrincipal actor, PermissionCode primary, PermissionCode fallback) {
        if (actor == null || !actor.active()) {
            throw new UnauthorizedOperationException(primary, "Active authenticated principal is required");
        }
        if (actor.activeBranchId() != null && !actor.canAccessBranch(actor.activeBranchId())) {
            throw new UnauthorizedOperationException(primary, "Principal is not authorized for active branch");
        }
        if (!actor.hasPermission(primary) && !actor.hasPermission(fallback)) {
            throw new UnauthorizedOperationException(primary, "Missing required permission: " + primary.code());
        }
    }

    private Set<BranchId> validateTenantBranches(TenantId tenantId, List<UUID> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return Set.of();
        }
        Set<BranchId> valid = new HashSet<>();
        for (UUID id : requestedIds) {
            BranchId branchId = BranchId.of(id);
            branchRepository.findByIdAndTenant(branchId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found in tenant: " + id));
            valid.add(branchId);
        }
        return valid;
    }

    private void enforceNoPrivilegeEscalation(AccessPrincipal actor, RoleDefinition role) {
        enforcePermissionSubset(actor, role.permissions());
    }

    private void enforcePermissionSubset(AccessPrincipal actor, Set<PermissionCode> permissionsToGrant) {
        if (actor.assignedRoles().contains("SUPER_ADMIN") || actor.assignedRoles().contains("RESTAURANT_OWNER")) {
            return;
        }
        for (PermissionCode permission : permissionsToGrant) {
            if (!actor.hasPermission(permission)) {
                throw new UnauthorizedOperationException(
                        permission,
                        "Privilege escalation denied: actor does not hold permission " + permission.code()
                );
            }
        }
    }

    private static Set<PermissionCode> parsePermissionCodes(Set<String> rawCodes) {
        if (rawCodes == null) {
            return Set.of();
        }
        Set<PermissionCode> parsed = new HashSet<>();
        for (String code : rawCodes) {
            PermissionCode resolved = PermissionCode.fromCode(code)
                    .orElseThrow(() -> new DomainException("INVALID_PERMISSION", "Unknown permission code: " + code));
            parsed.add(resolved);
        }
        return parsed;
    }

    private UserSummaryDto toUserSummary(TenantId tenantId, UserAccount user) {
        List<UUID> branches = userRepository.findAllowedBranches(tenantId, user.id()).stream()
                .map(BranchId::value)
                .sorted()
                .toList();
        List<UserRoleAssignmentDto> assignments = userRepository.findRoleAssignments(tenantId, user.id()).stream()
                .map(a -> new UserRoleAssignmentDto(a.roleId(), a.roleCode(), a.roleName(), a.branchId()))
                .toList();
        return new UserSummaryDto(
                user.id(),
                user.tenantId().value(),
                user.username(),
                user.fullName(),
                user.email(),
                user.active(),
                user.lockedUntil(),
                user.lastLoginAt(),
                branches,
                assignments
        );
    }

    private static RoleDto toRoleDto(RoleDefinition role) {
        return new RoleDto(
                role.id(),
                role.tenantId().value(),
                role.code(),
                role.name(),
                role.description(),
                role.scopeType(),
                role.systemTemplate(),
                role.active(),
                role.maxDiscountPct(),
                role.permissions().stream().map(PermissionCode::code).collect(Collectors.toSet()),
                role.version()
        );
    }

    private static AuditLogDto toAuditDto(AuditEntry entry) {
        return new AuditLogDto(
                entry.id(),
                entry.tenantId().value(),
                entry.branchId() != null ? entry.branchId().value() : null,
                entry.actorUserId(),
                entry.action().name(),
                entry.targetEntityType(),
                entry.targetEntityId(),
                entry.detailsJson(),
                entry.correlationId().value(),
                entry.occurredAt()
        );
    }
}
