package com.restaurantpos.application.payment;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.cash.CashMovementType;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.order.OrderAggregate;
import com.restaurantpos.domain.order.OrderStatePolicy;
import com.restaurantpos.domain.order.OrderStatus;
import com.restaurantpos.domain.payment.PaymentAttemptStatus;
import com.restaurantpos.domain.payment.PaymentCalculationPolicy;
import com.restaurantpos.domain.payment.PaymentMethod;
import com.restaurantpos.domain.payment.PaymentRecord;
import com.restaurantpos.domain.payment.PaymentStatePolicy;
import com.restaurantpos.domain.payment.PaymentTransactionEntry;
import com.restaurantpos.domain.payment.RefundRecord;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.PaymentRepositoryPort;
import com.restaurantpos.domain.port.PaymentReversalPort;
import com.restaurantpos.domain.port.PaymentTerminalPort;
import com.restaurantpos.domain.port.PosOrderRepositoryPort;
import com.restaurantpos.domain.port.SupervisorApprovalRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.security.SupervisorApproval;
import com.restaurantpos.shared.dto.payment.OrderPaymentSummaryDto;
import com.restaurantpos.shared.dto.payment.RefundDto;
import com.restaurantpos.shared.dto.payment.RefundRequest;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.Money;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles separate append-only refunds and payment reversals, supervisor threshold approvals,
 * non-self-approval checks, cash refund drawer balance posting, and refund receipt generation.
 * Never mutates inventory automatically.
 */
public class RefundService {

    private final PaymentRepositoryPort paymentRepo;
    private final PosOrderRepositoryPort orderRepo;
    private final SupervisorApprovalRepositoryPort approvalRepo;
    private final PaymentReversalPort reversalPort;
    private final CashSessionService cashSessionService;
    private final PaymentService paymentService;
    private final ReceiptService receiptService;
    private final BranchEventPublishPort eventPublisher;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;
    private final PaymentPolicyConfig policy;

    public RefundService(
            PaymentRepositoryPort paymentRepo,
            PosOrderRepositoryPort orderRepo,
            SupervisorApprovalRepositoryPort approvalRepo,
            PaymentReversalPort reversalPort,
            CashSessionService cashSessionService,
            PaymentService paymentService,
            ReceiptService receiptService,
            BranchEventPublishPort eventPublisher,
            AuditRecordingService auditService,
            SystemClockPort clock,
            PaymentPolicyConfig policy
    ) {
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
        this.approvalRepo = approvalRepo;
        this.reversalPort = reversalPort;
        this.cashSessionService = cashSessionService;
        this.paymentService = paymentService;
        this.receiptService = receiptService;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.clock = clock;
        this.policy = policy;
    }

    public OrderPaymentSummaryDto issueRefund(AccessPrincipal actor, RefundRequest req, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.PAYMENTS_REFUND);
        String idempKey = req.idempotencyKey().trim();

        Optional<RefundRecord> existing = paymentRepo.findRefundByIdempotency(actor.tenantId(), branchId, idempKey);
        if (existing.isPresent()) {
            return paymentService.buildSummary(actor.tenantId(), branchId, existing.get().orderId(), null, null);
        }

        OrderAggregate order = orderRepo.findByIdScoped(req.orderId(), actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found in branch"));
        PaymentRecord original = paymentRepo.findPaymentByIdScoped(req.originalPaymentId(), actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Original payment not found in branch"));
        if (!original.orderId().equals(order.id())) {
            throw new DomainException("PAYMENT_ORDER_MISMATCH", "Original payment does not belong to target order");
        }
        if (!"SETTLED".equals(original.status()) && !"CAPTURED".equals(original.status())) {
            throw new DomainException("PAYMENT_NOT_REFUNDABLE", "Only completed payments can be refunded");
        }

        List<RefundRecord> priorRefunds = paymentRepo.findRefundsByPaymentScoped(original.id(), actor.tenantId(), branchId);
        BigDecimal alreadyRefunded = priorRefunds.stream()
                .filter(r -> "COMPLETED".equals(r.status()))
                .map(RefundRecord::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Money requested = Money.of(req.amount(), original.currencyCode());
        PaymentCalculationPolicy.validateRefundCap(
                original.currencyCode(), original.amount(), alreadyRefunded, requested.amount());

        UUID approvedByUserId = resolveSupervisorApproval(actor, branchId, requested.amount(), req.supervisorApprovalId());
        PaymentMethod method = paymentRepo.findPaymentMethodScoped(original.paymentMethodId(), actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));

        Instant now = clock.now();
        String externalRef = null;
        if (!method.isCash() && !"VOUCHER".equalsIgnoreCase(method.methodType()) && original.externalReference() != null) {
            PaymentTerminalPort.TerminalResult rev = reversalPort.reverse(
                    original.externalReference(), requested.amount(), original.currencyCode(), req.simulatedOutcome());
            externalRef = rev.providerTxId();
            if (rev.outcome() != PaymentTerminalPort.Outcome.REVERSED
                    && rev.outcome() != PaymentTerminalPort.Outcome.APPROVED) {
                RefundRecord failed = paymentRepo.appendRefund(new RefundRecord(
                        UUID.randomUUID(), actor.tenantId(), branchId, order.id(), original.id(),
                        actor.userId(), approvedByUserId, idempKey, original.currencyCode(),
                        requested.amount(), "FAILED", req.reason().trim(), rev.message(), externalRef, now
                ));
                auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.REFUND_FAILED,
                        "Refund", failed.id().toString(), "{\"reason\":\"" + rev.message() + "\"}", corr);
                return paymentService.buildSummary(actor.tenantId(), branchId, order.id(), null,
                        "Refund declined by external terminal: " + rev.message());
            }
        }

        RefundRecord completedRefund = paymentRepo.appendRefund(new RefundRecord(
                UUID.randomUUID(), actor.tenantId(), branchId, order.id(), original.id(),
                actor.userId(), approvedByUserId, idempKey, original.currencyCode(),
                requested.amount(), "COMPLETED", req.reason().trim(), null,
                externalRef != null ? externalRef : "LOCAL-REF-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                now
        ));

        if (method.isCash()) {
            UUID cashSessId = req.cashSessionId() != null ? req.cashSessionId() : original.cashSessionId();
            cashSessionService.requireAndPostCashTransaction(
                    actor, branchId, cashSessId, CashMovementType.CASH_REFUND,
                    requested.amount(), "REF-" + completedRefund.id(),
                    "Cash refund for order " + order.orderNumber() + ": " + req.reason().trim(), corr
            );
        }

        // Update matching PaymentAttempt summary status cleanly without editing append-only PaymentRecord
        BigDecimal totalRefundedForPayment = alreadyRefunded.add(requested.amount());
        boolean paymentFullyRefunded = totalRefundedForPayment.compareTo(original.amount()) >= 0;
        paymentRepo.findAttemptByIdempotency(actor.tenantId(), branchId, original.idempotencyKey())
                .ifPresent(attempt -> {
                    PaymentAttemptStatus targetAttemptStatus = paymentFullyRefunded
                            ? PaymentAttemptStatus.REFUNDED
                            : PaymentAttemptStatus.PARTIALLY_REFUNDED;
                    if (PaymentStatePolicy.isTransitionAllowed(attempt.status(), targetAttemptStatus)) {
                        paymentRepo.saveAttempt(new com.restaurantpos.domain.payment.PaymentAttempt(
                                attempt.id(), attempt.tenantId(), attempt.branchId(), attempt.orderId(),
                                attempt.paymentMethodId(), attempt.cashSessionId(), attempt.initiatedByUserId(),
                                attempt.linkedPaymentId(), attempt.idempotencyKey(), attempt.currencyCode(),
                                attempt.requestedAmount(), attempt.tenderedAmount(), attempt.changeAmount(),
                                attempt.tipAmount(), targetAttemptStatus, attempt.providerName(),
                                attempt.providerTxId(), attempt.terminalRef(), attempt.maskedPan(),
                                attempt.cardBrand(), attempt.authCode(), attempt.responseCode(),
                                attempt.failureReason(), attempt.reconciled(), attempt.reconciledAt(),
                                attempt.reconciledByUserId(), attempt.createdAt(), now, attempt.version()
                        ));
                    }
                    paymentRepo.appendTransaction(new PaymentTransactionEntry(
                            UUID.randomUUID(), actor.tenantId(), branchId, original.id(), attempt.id(),
                            "REVERSE", requested.amount(), attempt.providerName(), completedRefund.externalReference(),
                            null, "00", now
                    ));
                });

        // Recompute order paid amount and status
        List<PaymentRecord> allPayments = paymentRepo.findByOrderScoped(order.id(), actor.tenantId(), branchId);
        List<RefundRecord> allRefunds = paymentRepo.findRefundsByOrderScoped(order.id(), actor.tenantId(), branchId);
        Money netPaid = Money.zero(order.currencyCode());
        for (PaymentRecord p : allPayments) {
            if ("SETTLED".equals(p.status()) || "CAPTURED".equals(p.status())) {
                netPaid = netPaid.add(Money.of(p.amount(), order.currencyCode()));
            }
        }
        for (RefundRecord r : allRefunds) {
            if ("COMPLETED".equals(r.status())) {
                netPaid = netPaid.subtract(Money.of(r.amount(), order.currencyCode()));
            }
        }
        if (netPaid.isNegative()) {
            netPaid = Money.zero(order.currencyCode());
        }

        OrderStatus nextOrderStatus = order.status();
        if (netPaid.isZero()) {
            nextOrderStatus = OrderStatus.REFUNDED;
        } else if (order.status() == OrderStatus.PAID || order.status() == OrderStatus.PARTIALLY_REFUNDED) {
            nextOrderStatus = OrderStatus.PARTIALLY_REFUNDED;
        }
        if (nextOrderStatus != order.status() && OrderStatePolicy.isMutableForItems(order.status())) {
            // Unsettled partial-paid order stays open while paidAmount decreases
            nextOrderStatus = order.status();
        } else if (nextOrderStatus != order.status()) {
            OrderStatePolicy.requireTransition(order.status(), nextOrderStatus);
        }

        OrderAggregate updatedOrder = orderRepo.save(new OrderAggregate(
                order.id(), order.tenantId(), order.branchId(), order.terminalId(), order.diningTableId(),
                order.createdByUserId(), order.assignedWaiterId(), order.orderNumber(), order.orderType(),
                nextOrderStatus, order.currencyCode(), order.subtotalAmount(), order.discountAmount(),
                order.taxAmount(), order.totalAmount(), netPaid.amount(), order.guestCount(),
                order.customerReference(), order.notes(), order.held(), order.openedAt(),
                order.sentToKitchenAt(), order.closedAt(), order.version(),
                order.items(), order.discounts(), order.taxes()
        ));

        if (nextOrderStatus != order.status()) {
            orderRepo.recordStatusHistory(UUID.randomUUID(), actor.tenantId(), branchId, updatedOrder.id(),
                    order.status(), nextOrderStatus, actor.userId(), "Refund: " + req.reason().trim(), now);
        }

        receiptService.createAndOptionallyPrintOrderReceipt(
                actor, branchId, updatedOrder, allPayments, allRefunds, original.id(), completedRefund.id(),
                "REFUND", null, corr
        );
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.REFUND_ISSUED,
                "Refund", completedRefund.id().toString(),
                "{\"originalPaymentId\":\"" + original.id() + "\",\"amount\":" + requested.amount() + "}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_UPDATED", updatedOrder.id());
        return paymentService.buildSummary(actor.tenantId(), branchId, updatedOrder.id(), null, null);
    }

    public List<RefundDto> listRefundsForOrder(AccessPrincipal actor, UUID orderId) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.PAYMENTS_VIEW);
        return paymentRepo.findRefundsByOrderScoped(orderId, actor.tenantId(), branchId).stream()
                .map(RefundService::toRefundDto)
                .toList();
    }

    private UUID resolveSupervisorApproval(
            AccessPrincipal actor,
            BranchId branchId,
            BigDecimal amount,
            UUID approvalId
    ) {
        if (approvalId != null) {
            SupervisorApproval approval = approvalRepo.findByIdScoped(approvalId, actor.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supervisor approval not found"));
            if (!"APPROVED".equals(approval.status()) || !approval.branchId().equals(branchId)) {
                throw new UnauthorizedOperationException(PermissionCode.PAYMENTS_REFUND, "Supervisor approval invalid for branch");
            }
            if (approval.expiresAt().isBefore(clock.now())) {
                throw new UnauthorizedOperationException(PermissionCode.PAYMENTS_REFUND, "Supervisor approval expired");
            }
            if (approval.approvedByUserId() == null || approval.approvedByUserId().equals(actor.userId())) {
                throw new UnauthorizedOperationException(PermissionCode.PAYMENTS_REFUND,
                        "Users cannot approve their own sensitive refunds");
            }
            if (approval.requiredPermission() != PermissionCode.PAYMENTS_REFUND) {
                throw new UnauthorizedOperationException(PermissionCode.PAYMENTS_REFUND, "Approval permission mismatch");
            }
            return approval.approvedByUserId();
        }

        if (amount.compareTo(policy.refundSupervisorThreshold()) > 0
                && !actor.hasPermission(PermissionCode.CASH_SESSION_RECONCILE)) {
            throw new UnauthorizedOperationException(
                    PermissionCode.PAYMENTS_REFUND,
                    "Refund above threshold (" + policy.refundSupervisorThreshold() + ") requires supervisor approval"
            );
        }
        return null;
    }

    public static RefundDto toRefundDto(RefundRecord r) {
        return new RefundDto(
                r.id(), r.orderId(), r.originalPaymentId(), r.issuedByUserId(), r.approvedByUserId(),
                r.idempotencyKey(), r.currencyCode(), r.amount(), r.status(), r.reason(),
                r.failureReason(), r.externalReference(), r.refundedAt()
        );
    }

    private static BranchId requireBranchPermission(AccessPrincipal actor, PermissionCode permission) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(permission, "Active branch session is required");
        }
        PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), permission);
        return actor.activeBranchId();
    }
}
