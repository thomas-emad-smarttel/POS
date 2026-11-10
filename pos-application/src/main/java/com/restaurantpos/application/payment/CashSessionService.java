package com.restaurantpos.application.payment;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.cash.CashMovement;
import com.restaurantpos.domain.cash.CashMovementType;
import com.restaurantpos.domain.cash.CashSession;
import com.restaurantpos.domain.cash.CashSessionCalculator;
import com.restaurantpos.domain.cash.CashSessionStatus;
import com.restaurantpos.domain.exception.ConcurrentModificationDomainException;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.payment.DrawerEventRecord;
import com.restaurantpos.domain.port.BranchRepositoryPort;
import com.restaurantpos.domain.port.CashDrawerPort;
import com.restaurantpos.domain.port.CashSessionRepositoryPort;
import com.restaurantpos.domain.port.PaymentRepositoryPort;
import com.restaurantpos.domain.port.SupervisorApprovalRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.security.SupervisorApproval;
import com.restaurantpos.domain.tenancy.Branch;
import com.restaurantpos.shared.dto.payment.CashMovementDto;
import com.restaurantpos.shared.dto.payment.CashSessionDto;
import com.restaurantpos.shared.dto.payment.CloseCashSessionRequest;
import com.restaurantpos.shared.dto.payment.DrawerEventDto;
import com.restaurantpos.shared.dto.payment.OpenCashSessionRequest;
import com.restaurantpos.shared.dto.payment.OpenDrawerRequest;
import com.restaurantpos.shared.dto.payment.RecordCashMovementRequest;
import com.restaurantpos.shared.dto.payment.ReopenCashSessionRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.Money;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages cashier shift sessions, append-only cash movements, derived expected cash balances,
 * reconciliation, authorized reopening, and RBAC-gated cash drawer operations.
 */
public class CashSessionService {

    private final CashSessionRepositoryPort sessionRepo;
    private final PaymentRepositoryPort paymentRepo;
    private final BranchRepositoryPort branchRepo;
    private final SupervisorApprovalRepositoryPort approvalRepo;
    private final CashDrawerPort cashDrawerPort;
    private final ReceiptService receiptService;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;

    public CashSessionService(
            CashSessionRepositoryPort sessionRepo,
            PaymentRepositoryPort paymentRepo,
            BranchRepositoryPort branchRepo,
            SupervisorApprovalRepositoryPort approvalRepo,
            CashDrawerPort cashDrawerPort,
            ReceiptService receiptService,
            AuditRecordingService auditService,
            SystemClockPort clock
    ) {
        this.sessionRepo = sessionRepo;
        this.paymentRepo = paymentRepo;
        this.branchRepo = branchRepo;
        this.approvalRepo = approvalRepo;
        this.cashDrawerPort = cashDrawerPort;
        this.receiptService = receiptService;
        this.auditService = auditService;
        this.clock = clock;
    }

    public CashSessionDto openSession(AccessPrincipal actor, OpenCashSessionRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.CASH_SESSION_OPEN);
        UUID terminalId = req.terminalId() != null ? req.terminalId() : actor.terminalId();
        if (terminalId == null) {
            throw new DomainException("TERMINAL_REQUIRED", "A terminal ID is required to open a cash session");
        }
        Optional<CashSession> active = sessionRepo.findActiveByTerminalScoped(actor.tenantId(), branchId, terminalId);
        if (active.isPresent()) {
            throw new DomainException("ACTIVE_SESSION_EXISTS",
                    "Terminal already has an active cash session (" + active.get().id() + ")");
        }

        Branch branch = branchRepo.findByIdAndTenant(branchId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        Instant now = clock.now();
        UUID sessionId = UUID.randomUUID();
        BigDecimal opening = Money.of(req.openingFloat(), branch.currencyCode()).amount();

        CashSession saved = sessionRepo.saveSession(new CashSession(
                sessionId, actor.tenantId(), branchId, terminalId, actor.userId(), null, null,
                branch.currencyCode(), opening, opening, null, null, CashSessionStatus.OPEN,
                now, null, null, null, 0L
        ));

        sessionRepo.appendMovement(new CashMovement(
                UUID.randomUUID(), actor.tenantId(), branchId, saved.id(), actor.userId(),
                CashMovementType.OPENING_FLOAT, opening, "Opening float", corr.value(),
                "OPENING-" + saved.id(), now
        ));

        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.CASH_SESSION_OPENED,
                "CashSession", saved.id().toString(), "{\"openingFloat\":" + opening + "}", corr);
        return getSessionDetail(actor, saved.id());
    }

    public Optional<CashSessionDto> findActiveSession(AccessPrincipal actor, UUID terminalId) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.REPORTS_CASH_VIEW);
        UUID resolvedTerminal = terminalId != null ? terminalId : actor.terminalId();
        if (resolvedTerminal == null) {
            return Optional.empty();
        }
        return sessionRepo.findActiveByTerminalScoped(actor.tenantId(), branchId, resolvedTerminal)
                .map(s -> buildDerivedDto(s, sessionRepo.findMovementsBySession(s.id(), actor.tenantId())));
    }

    public List<CashSessionDto> listBranchSessions(AccessPrincipal actor, int page, int size) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.REPORTS_CASH_VIEW);
        return sessionRepo.findSessionsByBranch(actor.tenantId(), branchId, page, Math.min(size, 100)).stream()
                .map(s -> buildDerivedDto(s, sessionRepo.findMovementsBySession(s.id(), actor.tenantId())))
                .toList();
    }

    public CashSessionDto getSessionDetail(AccessPrincipal actor, UUID sessionId) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.REPORTS_CASH_VIEW);
        CashSession session = sessionRepo.findSessionScoped(sessionId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash session not found in branch"));
        List<CashMovement> movements = sessionRepo.findMovementsBySession(session.id(), actor.tenantId());
        return buildDerivedDto(session, movements);
    }

    public CashSessionDto recordMovement(
            AccessPrincipal actor,
            UUID sessionId,
            RecordCashMovementRequest req,
            CorrelationId corr
    ) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.CASH_MOVEMENT_CREATE);
        CashSession session = sessionRepo.findSessionScoped(sessionId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash session not found in branch"));
        if (!session.status().isActive()) {
            throw new DomainException("CASH_SESSION_NOT_ACTIVE", "Cannot record movement on " + session.status() + " session");
        }

        if (req.idempotencyKey() != null && !req.idempotencyKey().isBlank()) {
            Optional<CashMovement> existing = sessionRepo.findMovementByIdempotency(
                    actor.tenantId(), branchId, req.idempotencyKey().trim());
            if (existing.isPresent()) {
                List<CashMovement> movements = sessionRepo.findMovementsBySession(session.id(), actor.tenantId());
                return buildDerivedDto(session, movements);
            }
        }

        CashMovementType type = CashMovementType.valueOf(req.movementType());
        BigDecimal amt = Money.of(req.amount(), session.currencyCode()).amount();
        Instant now = clock.now();

        sessionRepo.appendMovement(new CashMovement(
                UUID.randomUUID(), actor.tenantId(), branchId, session.id(), actor.userId(),
                type, amt, req.reason().trim(), corr.value(),
                req.idempotencyKey() != null && !req.idempotencyKey().isBlank() ? req.idempotencyKey().trim() : null,
                now
        ));

        List<CashMovement> movements = sessionRepo.findMovementsBySession(session.id(), actor.tenantId());
        Money derived = CashSessionCalculator.deriveExpectedBalance(session.currencyCode(), movements);
        CashSession updated = sessionRepo.saveSession(new CashSession(
                session.id(), session.tenantId(), session.branchId(), session.terminalId(),
                session.openedByUserId(), session.closedByUserId(), session.reopenedByUserId(),
                session.currencyCode(), session.openingFloat(), derived.amount(),
                session.countedCashAmount(), session.varianceAmount(), session.status(),
                session.openedAt(), session.closedAt(), session.reopenedAt(), session.reopenReason(),
                session.version()
        ));

        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.CASH_MOVEMENT_RECORDED,
                "CashSession", savedId(updated),
                "{\"type\":\"" + type + "\",\"amount\":" + amt + "}", corr);
        return buildDerivedDto(updated, movements);
    }

    public CashSessionDto closeSession(
            AccessPrincipal actor,
            UUID sessionId,
            CloseCashSessionRequest req,
            CorrelationId corr
    ) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.CASH_SESSION_CLOSE);
        CashSession session = sessionRepo.findSessionScoped(sessionId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash session not found in branch"));
        if (!session.status().isActive()) {
            throw new DomainException("CASH_SESSION_NOT_ACTIVE", "Cash session is already " + session.status());
        }
        if (session.version() != req.expectedVersion()) {
            throw new ConcurrentModificationDomainException(
                    "Cash session was modified concurrently", null);
        }

        List<CashMovement> movements = sessionRepo.findMovementsBySession(session.id(), actor.tenantId());
        Money expected = CashSessionCalculator.deriveExpectedBalance(session.currencyCode(), movements);
        Money counted = Money.of(req.countedCashAmount(), session.currencyCode());
        Money variance = CashSessionCalculator.computeVariance(session.currencyCode(), counted.amount(), expected.amount());

        Instant now = clock.now();
        CashSession closed = sessionRepo.saveSession(new CashSession(
                session.id(), session.tenantId(), session.branchId(), session.terminalId(),
                session.openedByUserId(), actor.userId(), session.reopenedByUserId(),
                session.currencyCode(), session.openingFloat(), expected.amount(),
                counted.amount(), variance.amount(), CashSessionStatus.CLOSED,
                session.openedAt(), now, session.reopenedAt(), session.reopenReason(),
                session.version()
        ));

        receiptService.generateCashSessionClosingReceipt(actor, branchId, closed, movements, corr);
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.CASH_SESSION_CLOSED,
                "CashSession", closed.id().toString(),
                "{\"expected\":" + expected.amount() + ",\"counted\":" + counted.amount() + ",\"variance\":" + variance.amount() + "}",
                corr);
        return buildDerivedDto(closed, movements);
    }

    public CashSessionDto reconcileSession(AccessPrincipal actor, UUID sessionId, long expectedVersion, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.CASH_SESSION_RECONCILE);
        CashSession session = sessionRepo.findSessionScoped(sessionId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash session not found in branch"));
        if (session.status() != CashSessionStatus.CLOSED) {
            throw new DomainException("CASH_SESSION_NOT_CLOSED", "Only CLOSED sessions can be reconciled");
        }
        if (session.version() != expectedVersion) {
            throw new ConcurrentModificationDomainException("Cash session modified concurrently", null);
        }

        List<CashMovement> movements = sessionRepo.findMovementsBySession(session.id(), actor.tenantId());
        Money expected = CashSessionCalculator.deriveExpectedBalance(session.currencyCode(), movements);
        CashSession reconciled = sessionRepo.saveSession(new CashSession(
                session.id(), session.tenantId(), session.branchId(), session.terminalId(),
                session.openedByUserId(), session.closedByUserId(), session.reopenedByUserId(),
                session.currencyCode(), session.openingFloat(), expected.amount(),
                session.countedCashAmount(), session.varianceAmount(), CashSessionStatus.RECONCILED,
                session.openedAt(), session.closedAt(), session.reopenedAt(), session.reopenReason(),
                session.version()
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.CASH_SESSION_RECONCILED,
                "CashSession", reconciled.id().toString(), "{\"variance\":" + reconciled.varianceAmount() + "}", corr);
        return buildDerivedDto(reconciled, movements);
    }

    public CashSessionDto reopenSession(
            AccessPrincipal actor,
            UUID sessionId,
            ReopenCashSessionRequest req,
            CorrelationId corr
    ) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.CASH_SESSION_REOPEN);
        CashSession session = sessionRepo.findSessionScoped(sessionId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash session not found in branch"));
        if (session.status() != CashSessionStatus.CLOSED) {
            throw new DomainException("CASH_SESSION_NOT_CLOSED", "Only CLOSED sessions can be reopened");
        }
        if (session.version() != req.expectedVersion()) {
            throw new ConcurrentModificationDomainException("Cash session modified concurrently", null);
        }
        Optional<CashSession> otherActive = sessionRepo.findActiveByTerminalScoped(
                actor.tenantId(), branchId, session.terminalId());
        if (otherActive.isPresent()) {
            throw new DomainException("ACTIVE_SESSION_EXISTS",
                    "Cannot reopen while another cash session is active on this terminal");
        }

        Instant now = clock.now();
        CashSession reopened = sessionRepo.saveSession(new CashSession(
                session.id(), session.tenantId(), session.branchId(), session.terminalId(),
                session.openedByUserId(), null, actor.userId(),
                session.currencyCode(), session.openingFloat(), session.expectedCashAmount(),
                null, null, CashSessionStatus.REOPENED,
                session.openedAt(), null, now, req.reason().trim(),
                session.version()
        ));
        List<CashMovement> movements = sessionRepo.findMovementsBySession(reopened.id(), actor.tenantId());
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.CASH_SESSION_REOPENED,
                "CashSession", reopened.id().toString(), "{\"reason\":\"" + req.reason().trim() + "\"}", corr);
        return buildDerivedDto(reopened, movements);
    }

    public DrawerEventDto openDrawerManually(AccessPrincipal actor, OpenDrawerRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.CASH_DRAWER_OPEN_WITHOUT_SALE);
        if (req.reason() == null || req.reason().isBlank()) {
            throw new DomainException("DRAWER_REASON_REQUIRED", "A reason is required to open the cash drawer without a sale");
        }
        if (req.supervisorApprovalId() != null) {
            validateSupervisorApproval(actor, branchId, req.supervisorApprovalId(), PermissionCode.CASH_DRAWER_OPEN_WITHOUT_SALE);
        }

        UUID terminalId = req.terminalId() != null ? req.terminalId() : actor.terminalId();
        UUID sessionId = req.cashSessionId();
        if (sessionId == null && terminalId != null) {
            sessionId = sessionRepo.findActiveByTerminalScoped(actor.tenantId(), branchId, terminalId)
                    .map(CashSession::id)
                    .orElse(null);
        }

        CashDrawerPort.DrawerOpenResult hw = cashDrawerPort.openDrawer();
        Instant now = clock.now();

        if (sessionId != null) {
            CashSession session = sessionRepo.findSessionScoped(sessionId, actor.tenantId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cash session not found in branch"));
            if (session.status().isActive()) {
                sessionRepo.appendMovement(new CashMovement(
                        UUID.randomUUID(), actor.tenantId(), branchId, session.id(), actor.userId(),
                        CashMovementType.DRAWER_NO_SALE, BigDecimal.ZERO, req.reason().trim(), corr.value(),
                        null, now
                ));
            }
        }

        DrawerEventRecord event = paymentRepo.appendDrawerEvent(new DrawerEventRecord(
                UUID.randomUUID(), actor.tenantId(), branchId, terminalId, sessionId, null, null,
                actor.userId(), req.supervisorApprovalId(), "MANUAL_NO_SALE", req.reason().trim(),
                hw.deviceName(), hw.hardwareStatus(), hw.openedOk(), hw.errorMessage(), corr.value(), now
        ));

        auditService.record(actor.tenantId(), branchId, actor.userId(),
                hw.openedOk() ? AuditAction.CASH_DRAWER_OPENED : AuditAction.CASH_DRAWER_FAILED,
                "CashDrawer", event.id().toString(),
                "{\"trigger\":\"MANUAL_NO_SALE\",\"openedOk\":" + hw.openedOk() + "}", corr);
        return toDrawerDto(event);
    }

    public CashSession requireAndPostCashTransaction(
            AccessPrincipal actor,
            BranchId branchId,
            UUID explicitSessionId,
            CashMovementType type,
            BigDecimal amount,
            String idempotencyKey,
            String reason,
            CorrelationId corr
    ) {
        CashSession session;
        if (explicitSessionId != null) {
            session = sessionRepo.findSessionScoped(explicitSessionId, actor.tenantId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cash session not found in branch"));
        } else if (actor.terminalId() != null) {
            session = sessionRepo.findActiveByTerminalScoped(actor.tenantId(), branchId, actor.terminalId())
                    .orElseThrow(() -> new DomainException(
                            "CASH_SESSION_REQUIRED", "An open cash session on this terminal is required for cash operations"));
        } else {
            throw new DomainException("CASH_SESSION_REQUIRED", "Specify an open cashSessionId or terminal cash session");
        }

        if (!session.status().isActive()) {
            throw new DomainException("CASH_SESSION_NOT_ACTIVE", "Target cash session is " + session.status());
        }

        if (idempotencyKey != null && sessionRepo.findMovementByIdempotency(actor.tenantId(), branchId, idempotencyKey).isPresent()) {
            return session;
        }

        sessionRepo.appendMovement(new CashMovement(
                UUID.randomUUID(), actor.tenantId(), branchId, session.id(), actor.userId(),
                type, amount, reason, corr.value(), idempotencyKey, clock.now()
        ));

        List<CashMovement> movements = sessionRepo.findMovementsBySession(session.id(), actor.tenantId());
        Money derived = CashSessionCalculator.deriveExpectedBalance(session.currencyCode(), movements);
        return sessionRepo.saveSession(new CashSession(
                session.id(), session.tenantId(), session.branchId(), session.terminalId(),
                session.openedByUserId(), session.closedByUserId(), session.reopenedByUserId(),
                session.currencyCode(), session.openingFloat(), derived.amount(),
                session.countedCashAmount(), session.varianceAmount(), session.status(),
                session.openedAt(), session.closedAt(), session.reopenedAt(), session.reopenReason(),
                session.version()
        ));
    }

    private void validateSupervisorApproval(AccessPrincipal actor, BranchId branchId, UUID approvalId, PermissionCode required) {
        SupervisorApproval approval = approvalRepo.findByIdScoped(approvalId, actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Supervisor approval not found"));
        if (!"APPROVED".equals(approval.status()) || !approval.branchId().equals(branchId)) {
            throw new UnauthorizedOperationException(required, "Supervisor approval is invalid for this branch");
        }
        if (approval.expiresAt().isBefore(clock.now())) {
            throw new UnauthorizedOperationException(required, "Supervisor approval has expired");
        }
        if (approval.approvedByUserId() == null || approval.approvedByUserId().equals(actor.userId())) {
            throw new UnauthorizedOperationException(required, "Users cannot self-approve sensitive operations");
        }
        if (approval.requiredPermission() != required) {
            throw new UnauthorizedOperationException(required, "Supervisor approval permission mismatch");
        }
    }

    public static CashSessionDto buildDerivedDto(CashSession s, List<CashMovement> movements) {
        Money derived = CashSessionCalculator.deriveExpectedBalance(s.currencyCode(), movements);
        List<CashMovementDto> moveDtos = movements.stream()
                .map(m -> new CashMovementDto(
                        m.id(), m.cashSessionId(), m.actorUserId(), m.movementType().name(),
                        m.amount(), m.reason(), m.correlationId(), m.occurredAt()
                ))
                .toList();
        return new CashSessionDto(
                s.id(), s.branchId().value(), s.terminalId(), s.openedByUserId(),
                s.closedByUserId(), s.reopenedByUserId(), s.currencyCode(),
                s.openingFloat(), derived.amount(), s.countedCashAmount(), s.varianceAmount(),
                s.status().name(), s.openedAt(), s.closedAt(), s.reopenedAt(), s.reopenReason(),
                s.version(), moveDtos
        );
    }

    public static DrawerEventDto toDrawerDto(DrawerEventRecord e) {
        return new DrawerEventDto(
                e.id(), e.branchId().value(), e.terminalId(), e.cashSessionId(),
                e.orderId(), e.paymentId(), e.actorUserId(), e.triggerType(),
                e.reason(), e.hardwareDeviceName(), e.hardwareStatus(),
                e.openedOk(), e.errorMessage(), e.occurredAt()
        );
    }

    private static String savedId(CashSession s) {
        return s.id().toString();
    }

    private static BranchId requireBranchPermission(AccessPrincipal actor, PermissionCode permission) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(permission, "Active branch session is required");
        }
        PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), permission);
        return actor.activeBranchId();
    }
}
