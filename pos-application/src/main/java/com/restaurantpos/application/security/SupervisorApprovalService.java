package com.restaurantpos.application.security;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.domain.exception.AuthenticationDomainException;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.port.PasswordHasherPort;
import com.restaurantpos.domain.port.SupervisorApprovalRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.port.UserRepositoryPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.security.SupervisorApproval;
import com.restaurantpos.domain.security.UserAccount;
import com.restaurantpos.shared.dto.security.SupervisorApprovalRequest;
import com.restaurantpos.shared.dto.security.SupervisorApprovalResponse;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Verifies supervisor credentials, tenant/branch scope, non-self-approval, and RBAC permission
 * before persisting an immutable approval or rejection record.
 */
public class SupervisorApprovalService {

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final EffectivePermissionResolver permissionResolver;
    private final SupervisorApprovalRepositoryPort approvalRepository;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;
    private final SecurityPolicyConfig policy;

    public SupervisorApprovalService(
            UserRepositoryPort userRepository,
            PasswordHasherPort passwordHasher,
            EffectivePermissionResolver permissionResolver,
            SupervisorApprovalRepositoryPort approvalRepository,
            AuditRecordingService auditService,
            SystemClockPort clock,
            SecurityPolicyConfig policy
    ) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.permissionResolver = Objects.requireNonNull(permissionResolver);
        this.approvalRepository = Objects.requireNonNull(approvalRepository);
        this.auditService = Objects.requireNonNull(auditService);
        this.clock = Objects.requireNonNull(clock);
        this.policy = Objects.requireNonNull(policy);
    }

    public SupervisorApprovalResponse authorize(
            AccessPrincipal requester,
            SupervisorApprovalRequest request,
            CorrelationId correlationId
    ) {
        if (requester == null || !requester.active() || requester.activeBranchId() == null) {
            throw new AuthenticationDomainException("AUTH_REQUIRED", "Active branch session is required");
        }
        PermissionCode requiredPermission = PermissionCode.fromCode(request.requiredPermission())
                .orElseThrow(() -> new DomainException("INVALID_PERMISSION", "Unknown permission: " + request.requiredPermission()));

        Instant now = clock.now();
        UserAccount supervisor = userRepository.findByTenantAndUsername(
                        requester.tenantId(),
                        request.supervisorUsername().trim()
                )
                .orElseThrow(() -> new AuthenticationDomainException("AUTH_INVALID_CREDENTIALS", "Invalid supervisor credentials"));

        if (supervisor.id().equals(requester.userId())) {
            recordRejected(requester, supervisor.id(), requiredPermission, request, correlationId, now, "self_approval_forbidden");
            throw new UnauthorizedOperationException(requiredPermission, "Users cannot approve their own sensitive actions");
        }

        if (!supervisor.active() || supervisor.isLockedAt(now)
                || !passwordHasher.matches(request.supervisorPassword(), supervisor.passwordHash())) {
            recordRejected(requester, supervisor.id(), requiredPermission, request, correlationId, now, "invalid_supervisor_credentials");
            throw new AuthenticationDomainException("AUTH_INVALID_CREDENTIALS", "Invalid supervisor credentials");
        }

        AccessPrincipal supervisorPrincipal = permissionResolver.resolve(
                supervisor,
                requester.activeBranchId(),
                request.terminalId(),
                null
        );

        if (!supervisorPrincipal.canAccessBranch(requester.activeBranchId())
                || !supervisorPrincipal.hasPermission(requiredPermission)) {
            recordRejected(requester, supervisor.id(), requiredPermission, request, correlationId, now, "supervisor_lacks_permission_or_branch");
            throw new UnauthorizedOperationException(
                    requiredPermission,
                    "Supervisor lacks branch access or permission " + requiredPermission.code()
            );
        }

        Instant expiresAt = now.plus(policy.approvalTtlMinutes(), ChronoUnit.MINUTES);
        UUID approvalId = UUID.randomUUID();
        SupervisorApproval saved = approvalRepository.save(new SupervisorApproval(
                approvalId,
                requester.tenantId(),
                requester.activeBranchId(),
                request.terminalId() != null ? request.terminalId() : requester.terminalId(),
                requiredPermission,
                request.targetEntityType().trim(),
                request.targetEntityId().trim(),
                requester.userId(),
                supervisor.id(),
                "APPROVED",
                request.reason().trim(),
                correlationId,
                expiresAt,
                now
        ));

        auditService.record(
                requester.tenantId(),
                requester.activeBranchId(),
                supervisor.id(),
                AuditAction.SUPERVISOR_APPROVAL_GRANTED,
                request.targetEntityType().trim(),
                request.targetEntityId().trim(),
                "{\"requestedBy\":\"" + requester.userId() + "\",\"permission\":\"" + requiredPermission.code() + "\"}",
                correlationId
        );

        return new SupervisorApprovalResponse(
                saved.id(),
                saved.requestedByUserId(),
                saved.approvedByUserId(),
                saved.status(),
                saved.expiresAt()
        );
    }

    private void recordRejected(
            AccessPrincipal requester,
            UUID supervisorId,
            PermissionCode requiredPermission,
            SupervisorApprovalRequest request,
            CorrelationId correlationId,
            Instant now,
            String failureReason
    ) {
        approvalRepository.save(new SupervisorApproval(
                UUID.randomUUID(),
                requester.tenantId(),
                requester.activeBranchId(),
                request.terminalId() != null ? request.terminalId() : requester.terminalId(),
                requiredPermission,
                request.targetEntityType().trim(),
                request.targetEntityId().trim(),
                requester.userId(),
                supervisorId.equals(requester.userId()) ? null : supervisorId,
                "REJECTED",
                request.reason().trim(),
                correlationId,
                now,
                now
        ));
        auditService.record(
                requester.tenantId(),
                requester.activeBranchId(),
                requester.userId(),
                AuditAction.SUPERVISOR_APPROVAL_REJECTED,
                request.targetEntityType().trim(),
                request.targetEntityId().trim(),
                "{\"reason\":\"" + failureReason + "\",\"permission\":\"" + requiredPermission.code() + "\"}",
                correlationId
        );
    }
}
