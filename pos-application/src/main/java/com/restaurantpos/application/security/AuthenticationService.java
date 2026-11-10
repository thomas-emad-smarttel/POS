package com.restaurantpos.application.security;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.domain.exception.AuthenticationDomainException;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.port.PasswordHasherPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.port.TenantQueryPort;
import com.restaurantpos.domain.port.UserRepositoryPort;
import com.restaurantpos.domain.port.UserSessionRepositoryPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.security.UserAccount;
import com.restaurantpos.domain.security.UserSession;
import com.restaurantpos.domain.tenancy.Tenant;
import com.restaurantpos.shared.dto.security.ChangePasswordRequest;
import com.restaurantpos.shared.dto.security.LoginRequest;
import com.restaurantpos.shared.dto.security.LoginResponse;
import com.restaurantpos.shared.dto.security.UserProfileDto;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Handles secure authentication, session creation/validation/revocation, branch switching,
 * lockout protection, and password updates.
 */
public class AuthenticationService {

    private static final String INVALID_CREDENTIALS_MSG = "Invalid username or password";

    private final TenantQueryPort tenantQuery;
    private final UserRepositoryPort userRepository;
    private final UserSessionRepositoryPort sessionRepository;
    private final PasswordHasherPort passwordHasher;
    private final EffectivePermissionResolver permissionResolver;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;
    private final SecurityPolicyConfig policy;
    private final LoginThrottle throttle;

    public AuthenticationService(
            TenantQueryPort tenantQuery,
            UserRepositoryPort userRepository,
            UserSessionRepositoryPort sessionRepository,
            PasswordHasherPort passwordHasher,
            EffectivePermissionResolver permissionResolver,
            AuditRecordingService auditService,
            SystemClockPort clock,
            SecurityPolicyConfig policy
    ) {
        this.tenantQuery = Objects.requireNonNull(tenantQuery);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.permissionResolver = Objects.requireNonNull(permissionResolver);
        this.auditService = Objects.requireNonNull(auditService);
        this.clock = Objects.requireNonNull(clock);
        this.policy = Objects.requireNonNull(policy);
        this.throttle = new LoginThrottle(policy.throttleWindowSeconds(), policy.maxThrottleAttemptsPerKey());
    }

    public LoginResponse login(LoginRequest request, CorrelationId correlationId) {
        Instant now = clock.now();
        String throttleKey = request.tenantCode().trim().toLowerCase() + ":" + request.username().trim().toLowerCase();
        throttle.checkAndRecordAttempt(throttleKey, now);

        Tenant tenant = tenantQuery.findByCode(request.tenantCode().trim())
                .filter(t -> "ACTIVE".equals(t.status()))
                .orElseThrow(() -> new AuthenticationDomainException("AUTH_INVALID_CREDENTIALS", INVALID_CREDENTIALS_MSG));
        TenantId tenantId = TenantId.of(tenant.id());

        UserAccount user = userRepository.findByTenantAndUsername(tenantId, request.username().trim())
                .orElseThrow(() -> new AuthenticationDomainException("AUTH_INVALID_CREDENTIALS", INVALID_CREDENTIALS_MSG));

        if (!user.active()) {
            auditLoginFailure(tenantId, user.id(), "account_inactive", correlationId);
            throw new AuthenticationDomainException("AUTH_INVALID_CREDENTIALS", INVALID_CREDENTIALS_MSG);
        }

        if (user.isLockedAt(now)) {
            auditLoginFailure(tenantId, user.id(), "account_locked", correlationId);
            throw new AuthenticationDomainException("AUTH_ACCOUNT_LOCKED", "Account is temporarily locked due to failed login attempts");
        }

        if (!passwordHasher.matches(request.password(), user.passwordHash())) {
            handleFailedPasswordAttempt(user, now, correlationId);
            throw new AuthenticationDomainException("AUTH_INVALID_CREDENTIALS", INVALID_CREDENTIALS_MSG);
        }

        Set<BranchId> allowedBranches = userRepository.findAllowedBranches(tenantId, user.id());
        if (allowedBranches.isEmpty()) {
            auditLoginFailure(tenantId, user.id(), "no_branch_access", correlationId);
            throw new UnauthorizedOperationException(PermissionCode.DASHBOARD_VIEW, "User has no authorized branches");
        }

        BranchId activeBranch = resolveInitialBranch(request.branchId(), allowedBranches, tenantId, user.id(), correlationId);

        UserAccount refreshedUser = new UserAccount(
                user.id(), user.tenantId(), user.username(), user.fullName(), user.email(),
                user.passwordHash(), user.active(), 0, null, now, user.version()
        );
        userRepository.save(refreshedUser);
        throttle.reset(throttleKey);

        enforceMaxConcurrentSessions(tenantId, user.id(), now, correlationId);

        String rawToken = TokenSupport.generateOpaqueToken();
        String tokenHash = TokenSupport.sha256Hex(rawToken);
        Instant expiresAt = now.plus(policy.sessionTtlMinutes(), ChronoUnit.MINUTES);
        UUID sessionId = UUID.randomUUID();

        UserSession session = new UserSession(
                sessionId,
                tenantId,
                activeBranch,
                user.id(),
                request.terminalId(),
                tokenHash,
                expiresAt,
                null,
                now
        );
        sessionRepository.create(session);

        AccessPrincipal principal = permissionResolver.resolve(refreshedUser, activeBranch, request.terminalId(), sessionId);

        auditService.record(
                tenantId,
                activeBranch,
                user.id(),
                AuditAction.LOGIN_SUCCESS,
                "UserSession",
                sessionId.toString(),
                "{\"terminalId\":\"" + (request.terminalId() != null ? request.terminalId() : "unspecified") + "\"}",
                correlationId
        );

        return new LoginResponse(rawToken, "Bearer", expiresAt, toProfileDto(refreshedUser, principal, expiresAt));
    }

    public AccessPrincipal authenticateToken(String rawBearerToken) {
        if (rawBearerToken == null || rawBearerToken.isBlank()) {
            throw new AuthenticationDomainException("AUTH_REQUIRED", "Missing authentication token");
        }
        Instant now = clock.now();
        String tokenHash = TokenSupport.sha256Hex(rawBearerToken.trim());

        UserSession session = sessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthenticationDomainException("AUTH_SESSION_INVALID", "Session is invalid"));

        if (!session.isValidAt(now)) {
            throw new AuthenticationDomainException("AUTH_SESSION_EXPIRED", "Session has expired or been revoked");
        }

        UserAccount user = userRepository.findByIdAndTenant(session.userId(), session.tenantId())
                .orElseThrow(() -> new AuthenticationDomainException("AUTH_SESSION_INVALID", "User account no longer exists"));

        if (!user.active() || user.isLockedAt(now)) {
            throw new AuthenticationDomainException("AUTH_ACCOUNT_INACTIVE", "User account is inactive or locked");
        }

        AccessPrincipal principal = permissionResolver.resolve(
                user,
                session.branchId(),
                session.terminalId(),
                session.id()
        );
        if (session.branchId() != null && !principal.canAccessBranch(session.branchId())) {
            throw new UnauthorizedOperationException(
                    PermissionCode.DASHBOARD_VIEW,
                    "Branch authorization for active session has been revoked"
            );
        }
        return principal;
    }

    public UserProfileDto getCurrentProfile(AccessPrincipal principal) {
        UserAccount user = userRepository.findByIdAndTenant(principal.userId(), principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        UserSession session = principal.sessionId() != null
                ? sessionRepository.findByIdAndTenant(principal.sessionId(), principal.tenantId()).orElse(null)
                : null;
        return toProfileDto(user, principal, session != null ? session.expiresAt() : null);
    }

    public UserProfileDto switchBranch(AccessPrincipal principal, UUID targetBranchId, CorrelationId correlationId) {
        BranchId branchId = BranchId.of(targetBranchId);
        if (!principal.canAccessBranch(branchId)) {
            auditService.record(
                    principal.tenantId(),
                    principal.activeBranchId(),
                    principal.userId(),
                    AuditAction.AUTHORIZATION_DENIED,
                    "Branch",
                    targetBranchId.toString(),
                    "{\"reason\":\"unauthorized_branch_switch\"}",
                    correlationId
            );
            throw new UnauthorizedOperationException(PermissionCode.DASHBOARD_VIEW, "Not authorized to switch to branch " + targetBranchId);
        }
        if (principal.sessionId() != null) {
            sessionRepository.updateActiveBranch(principal.sessionId(), principal.tenantId(), branchId);
        }
        UserAccount user = userRepository.findByIdAndTenant(principal.userId(), principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        AccessPrincipal updatedPrincipal = permissionResolver.resolve(
                user, branchId, principal.terminalId(), principal.sessionId()
        );
        UserSession session = principal.sessionId() != null
                ? sessionRepository.findByIdAndTenant(principal.sessionId(), principal.tenantId()).orElse(null)
                : null;

        auditService.record(
                principal.tenantId(),
                branchId,
                principal.userId(),
                AuditAction.BRANCH_SWITCHED,
                "Branch",
                targetBranchId.toString(),
                "{\"sessionId\":\"" + principal.sessionId() + "\"}",
                correlationId
        );
        return toProfileDto(user, updatedPrincipal, session != null ? session.expiresAt() : null);
    }

    public void logout(AccessPrincipal principal, CorrelationId correlationId) {
        if (principal.sessionId() == null) {
            return;
        }
        Instant now = clock.now();
        sessionRepository.revokeSession(principal.sessionId(), principal.tenantId(), now);
        auditService.record(
                principal.tenantId(),
                principal.activeBranchId(),
                principal.userId(),
                AuditAction.LOGOUT,
                "UserSession",
                principal.sessionId().toString(),
                null,
                correlationId
        );
    }

    public void changePassword(AccessPrincipal principal, ChangePasswordRequest request, CorrelationId correlationId) {
        UserAccount user = userRepository.findByIdAndTenant(principal.userId(), principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordHasher.matches(request.currentPassword(), user.passwordHash())) {
            auditService.record(
                    principal.tenantId(),
                    principal.activeBranchId(),
                    principal.userId(),
                    AuditAction.AUTHORIZATION_DENIED,
                    "User",
                    user.id().toString(),
                    "{\"reason\":\"wrong_current_password\"}",
                    correlationId
            );
            throw new AuthenticationDomainException("AUTH_INVALID_CREDENTIALS", "Current password is incorrect");
        }
        if (passwordHasher.matches(request.newPassword(), user.passwordHash())) {
            throw new DomainException("PASSWORD_REUSE", "New password must differ from current password");
        }

        String newHash = passwordHasher.hash(request.newPassword());
        UserAccount updated = new UserAccount(
                user.id(), user.tenantId(), user.username(), user.fullName(), user.email(),
                newHash, user.active(), 0, null, user.lastLoginAt(), user.version()
        );
        userRepository.save(updated);

        Instant now = clock.now();
        sessionRepository.revokeAllForUser(principal.tenantId(), user.id(), now);

        auditService.record(
                principal.tenantId(),
                principal.activeBranchId(),
                user.id(),
                AuditAction.PASSWORD_CHANGED,
                "User",
                user.id().toString(),
                "{\"sessionsRevoked\":true}",
                correlationId
        );
    }

    private BranchId resolveInitialBranch(
            UUID requestedBranchId,
            Set<BranchId> allowedBranches,
            TenantId tenantId,
            UUID userId,
            CorrelationId correlationId
    ) {
        if (requestedBranchId != null) {
            BranchId candidate = BranchId.of(requestedBranchId);
            if (!allowedBranches.contains(candidate)) {
                auditLoginFailure(tenantId, userId, "unauthorized_branch", correlationId);
                throw new UnauthorizedOperationException(PermissionCode.DASHBOARD_VIEW, "User does not have access to requested branch");
            }
            return candidate;
        }
        return allowedBranches.stream()
                .min(Comparator.comparing(b -> b.value().toString()))
                .orElseThrow();
    }

    private void handleFailedPasswordAttempt(UserAccount user, Instant now, CorrelationId correlationId) {
        int nextCount = user.failedLoginCount() + 1;
        boolean shouldLock = nextCount >= policy.maxFailedLoginAttempts();
        Instant lockedUntil = shouldLock ? now.plus(policy.lockoutDurationMinutes(), ChronoUnit.MINUTES) : null;

        UserAccount updated = new UserAccount(
                user.id(), user.tenantId(), user.username(), user.fullName(), user.email(),
                user.passwordHash(), user.active(), nextCount, lockedUntil, user.lastLoginAt(), user.version()
        );
        userRepository.save(updated);

        auditLoginFailure(user.tenantId(), user.id(), shouldLock ? "locked_after_failures" : "invalid_password", correlationId);
        if (shouldLock) {
            auditService.record(
                    user.tenantId(),
                    null,
                    user.id(),
                    AuditAction.ACCOUNT_LOCKED,
                    "User",
                    user.id().toString(),
                    "{\"failedAttempts\":" + nextCount + "}",
                    correlationId
            );
        }
    }

    private void enforceMaxConcurrentSessions(TenantId tenantId, UUID userId, Instant now, CorrelationId correlationId) {
        List<UserSession> active = sessionRepository.findActiveByUser(tenantId, userId).stream()
                .filter(s -> s.isValidAt(now))
                .sorted(Comparator.comparing(UserSession::createdAt))
                .toList();
        int excess = active.size() - policy.maxActiveSessionsPerUser() + 1;
        for (int i = 0; i < excess; i++) {
            UserSession oldest = active.get(i);
            sessionRepository.revokeSession(oldest.id(), tenantId, now);
            auditService.record(
                    tenantId,
                    oldest.branchId(),
                    userId,
                    AuditAction.SESSION_REVOKED,
                    "UserSession",
                    oldest.id().toString(),
                    "{\"reason\":\"max_sessions_exceeded\"}",
                    correlationId
            );
        }
    }

    private void auditLoginFailure(TenantId tenantId, UUID userId, String reason, CorrelationId correlationId) {
        auditService.record(
                tenantId,
                null,
                userId,
                AuditAction.LOGIN_FAILED,
                "User",
                userId != null ? userId.toString() : "unknown",
                "{\"reason\":\"" + reason + "\"}",
                correlationId
        );
    }

    private static UserProfileDto toProfileDto(UserAccount user, AccessPrincipal principal, Instant expiresAt) {
        return new UserProfileDto(
                user.id(),
                user.tenantId().value(),
                principal.activeBranchId() != null ? principal.activeBranchId().value() : null,
                principal.terminalId(),
                principal.sessionId(),
                user.username(),
                user.fullName(),
                user.email(),
                user.active(),
                principal.allowedBranches().stream().map(BranchId::value).sorted().toList(),
                principal.assignedRoles(),
                principal.effectivePermissions().stream().map(PermissionCode::code).collect(java.util.stream.Collectors.toSet()),
                expiresAt
        );
    }
}
