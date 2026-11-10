package com.restaurantpos.application.payment;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.cash.CashMovementType;
import com.restaurantpos.domain.cash.CashSession;
import com.restaurantpos.domain.exception.DomainException;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.order.OrderAggregate;
import com.restaurantpos.domain.order.OrderStatePolicy;
import com.restaurantpos.domain.order.OrderStatus;
import com.restaurantpos.domain.payment.DrawerEventRecord;
import com.restaurantpos.domain.payment.PaymentAttempt;
import com.restaurantpos.domain.payment.PaymentAttemptStatus;
import com.restaurantpos.domain.payment.PaymentCalculationPolicy;
import com.restaurantpos.domain.payment.PaymentMethod;
import com.restaurantpos.domain.payment.PaymentRecord;
import com.restaurantpos.domain.payment.PaymentStatePolicy;
import com.restaurantpos.domain.payment.PaymentTransactionEntry;
import com.restaurantpos.domain.payment.RefundRecord;
import com.restaurantpos.domain.port.BranchEventPublishPort;
import com.restaurantpos.domain.port.CashDrawerPort;
import com.restaurantpos.domain.port.PaymentAuthorizationPort;
import com.restaurantpos.domain.port.PaymentRepositoryPort;
import com.restaurantpos.domain.port.PaymentStatusQueryPort;
import com.restaurantpos.domain.port.PaymentTerminalPort;
import com.restaurantpos.domain.port.PosOrderRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.port.TableRepositoryPort;
import com.restaurantpos.domain.receipt.ReceiptRecord;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.table.DiningTable;
import com.restaurantpos.domain.table.TableStatus;
import com.restaurantpos.shared.dto.payment.OrderPaymentSummaryDto;
import com.restaurantpos.shared.dto.payment.PaymentAttemptDto;
import com.restaurantpos.shared.dto.payment.PaymentDto;
import com.restaurantpos.shared.dto.payment.PaymentMethodDto;
import com.restaurantpos.shared.dto.payment.ProcessPaymentRequest;
import com.restaurantpos.shared.dto.payment.ReceiptDto;
import com.restaurantpos.shared.dto.payment.RefundDto;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.model.Money;
import com.restaurantpos.shared.model.TenantId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Authoritative Local Server payment orchestration enforcing balance calculation, idempotency,
 * split/partial tenders, cash change, cash session posting, card terminal state transitions,
 * drawer kick isolation, receipt persistence, and RBAC/audit logging.
 */
public class PaymentService {

    private final PaymentRepositoryPort paymentRepo;
    private final PosOrderRepositoryPort orderRepo;
    private final TableRepositoryPort tableRepo;
    private final CashSessionService cashSessionService;
    private final ReceiptService receiptService;
    private final PaymentTerminalPort terminalPort;
    private final PaymentAuthorizationPort authPort;
    private final PaymentStatusQueryPort statusQueryPort;
    private final CashDrawerPort cashDrawerPort;
    private final BranchEventPublishPort eventPublisher;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;

    public PaymentService(
            PaymentRepositoryPort paymentRepo,
            PosOrderRepositoryPort orderRepo,
            TableRepositoryPort tableRepo,
            CashSessionService cashSessionService,
            ReceiptService receiptService,
            PaymentTerminalPort terminalPort,
            PaymentAuthorizationPort authPort,
            PaymentStatusQueryPort statusQueryPort,
            CashDrawerPort cashDrawerPort,
            BranchEventPublishPort eventPublisher,
            AuditRecordingService auditService,
            SystemClockPort clock
    ) {
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
        this.tableRepo = tableRepo;
        this.cashSessionService = cashSessionService;
        this.receiptService = receiptService;
        this.terminalPort = terminalPort;
        this.authPort = authPort;
        this.statusQueryPort = statusQueryPort;
        this.cashDrawerPort = cashDrawerPort;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.clock = clock;
    }

    public List<PaymentMethodDto> listPaymentMethods(AccessPrincipal actor) {
        if (actor == null) {
            throw new UnauthorizedOperationException(PermissionCode.PAYMENTS_VIEW, "Authentication required");
        }
        List<PaymentMethod> methods = paymentRepo.findPaymentMethodsByTenant(actor.tenantId());
        if (methods.isEmpty()) {
            methods = seedDefaultMethods(actor.tenantId());
        }
        return methods.stream()
                .filter(PaymentMethod::active)
                .map(m -> new PaymentMethodDto(m.id(), m.code(), m.name(), m.methodType(), m.opensDrawer(), m.active()))
                .toList();
    }

    public OrderPaymentSummaryDto getOrderPaymentSummary(AccessPrincipal actor, UUID orderId) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.PAYMENTS_VIEW);
        return buildSummary(actor.tenantId(), branchId, orderId, null, null);
    }

    public OrderPaymentSummaryDto processPayment(
            AccessPrincipal actor,
            ProcessPaymentRequest req,
            CorrelationId corr
    ) {
        BranchId branchId = requireProcessPermission(actor);
        String idempKey = req.idempotencyKey().trim();

        // 1. Strict idempotency guard before charging anything
        Optional<PaymentAttempt> existingAttempt = paymentRepo.findAttemptByIdempotency(actor.tenantId(), branchId, idempKey);
        if (existingAttempt.isPresent()) {
            return buildSummary(actor.tenantId(), branchId, existingAttempt.get().orderId(), existingAttempt.get(), null);
        }

        // 2. Load order & validate payable state
        OrderAggregate order = orderRepo.findByIdScoped(req.orderId(), actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found in branch"));
        PaymentCalculationPolicy.requireOrderPayable(order.status());

        // 3. Load active payment method
        PaymentMethod method = paymentRepo.findPaymentMethodScoped(req.paymentMethodId(), actor.tenantId())
                .filter(PaymentMethod::active)
                .orElseThrow(() -> new ResourceNotFoundException("Active payment method not found"));

        // 4. Authoritative remaining due calculation
        List<PaymentRecord> existingPayments = paymentRepo.findByOrderScoped(order.id(), actor.tenantId(), branchId);
        List<RefundRecord> existingRefunds = paymentRepo.findRefundsByOrderScoped(order.id(), actor.tenantId(), branchId);
        BigDecimal netPaid = computeNetPaid(existingPayments, existingRefunds, order.currencyCode());
        Money remainingDue = PaymentCalculationPolicy.computeRemainingDue(order.currencyCode(), order.totalAmount(), netPaid);

        PaymentCalculationPolicy.TenderCalculation calc = PaymentCalculationPolicy.validateAndComputeTender(
                order.currencyCode(), remainingDue.amount(), req.amount(), req.tenderedAmount(), method.isCash());
        Money tip = req.tipAmount() != null
                ? Money.of(req.tipAmount(), order.currencyCode())
                : Money.zero(order.currencyCode());

        // 5. Require active cash session for CASH tenders before starting
        CashSession targetCashSession = null;
        if (method.isCash()) {
            UUID cashSessId = req.cashSessionId();
            if (cashSessId != null) {
                var dto = cashSessionService.getSessionDetail(actor, cashSessId);
                if (!"OPEN".equals(dto.status()) && !"REOPENED".equals(dto.status())) {
                    throw new DomainException("CASH_SESSION_NOT_ACTIVE", "Target cash session is not open");
                }
                targetCashSession = new CashSession(dto.id(), actor.tenantId(), branchId, dto.terminalId(),
                        dto.openedByUserId(), null, null, dto.currencyCode(), dto.openingFloat(),
                        dto.derivedExpectedCash(), null, null, null, dto.openedAt(), null, null, null, dto.version());
            } else if (actor.terminalId() != null) {
                var activeDto = cashSessionService.findActiveSession(actor, actor.terminalId())
                        .orElseThrow(() -> new DomainException("CASH_SESSION_REQUIRED",
                                "Open a cash session on this terminal before processing cash payments"));
                targetCashSession = new CashSession(activeDto.id(), actor.tenantId(), branchId, activeDto.terminalId(),
                        activeDto.openedByUserId(), null, null, activeDto.currencyCode(), activeDto.openingFloat(),
                        activeDto.derivedExpectedCash(), null, null, null, activeDto.openedAt(), null, null, null, activeDto.version());
            } else {
                throw new DomainException("CASH_SESSION_REQUIRED", "An active cash session is required for cash payments");
            }
        }

        Instant now = clock.now();
        UUID attemptId = UUID.randomUUID();
        String providerName = method.isCash() ? "LOCAL_CASH" : "EXTERNAL_TERMINAL";

        // 6. Create PENDING -> PROCESSING attempt before invoking external device/terminal
        PaymentAttempt pending = paymentRepo.saveAttempt(new PaymentAttempt(
                attemptId, actor.tenantId(), branchId, order.id(), method.id(),
                targetCashSession != null ? targetCashSession.id() : req.cashSessionId(),
                actor.userId(), null, idempKey, order.currencyCode(),
                calc.appliedAmount().amount(), calc.tenderedAmount().amount(), calc.changeAmount().amount(),
                tip.amount(), PaymentAttemptStatus.PENDING, providerName, null, req.terminalRef(),
                null, null, null, null, null, false, null, null, now, now, 0L
        ));

        PaymentStatePolicy.requireTransition(pending.status(), PaymentAttemptStatus.PROCESSING);
        PaymentAttempt processing = paymentRepo.saveAttempt(withStatus(pending, PaymentAttemptStatus.PROCESSING, now));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.PAYMENT_STARTED,
                "PaymentAttempt", processing.id().toString(),
                "{\"method\":\"" + method.code() + "\",\"amount\":" + calc.appliedAmount().amount() + "}", corr);

        if (method.isCash() || "VOUCHER".equalsIgnoreCase(method.methodType())) {
            return settleCompletedAttempt(actor, branchId, order, method, processing,
                    "LOCAL-" + attemptId.toString().substring(0, 8).toUpperCase(),
                    null, null, "OK", "00", method.code(), corr);
        }

        // 7. External card / digital wallet terminal authorization
        PaymentTerminalPort.TerminalResult result = authPort.authorize(new PaymentTerminalPort.StartCommand(
                idempKey,
                order.currencyCode(),
                calc.appliedAmount().amount().add(tip.amount()),
                req.terminalRef(),
                req.simulatedOutcome()
        ));

        return switch (result.outcome()) {
            case APPROVED -> settleCompletedAttempt(actor, branchId, order, method, processing,
                    result.providerTxId(), result.maskedPan(), result.cardBrand(),
                    result.authCode(), result.responseCode(), result.providerName(), corr);
            case DECLINED -> failAttempt(actor, branchId, processing, result, corr);
            case CANCELLED -> cancelAttemptFromGateway(actor, branchId, processing, result, corr);
            case UNCERTAIN_TIMEOUT -> markUncertainAttempt(actor, branchId, processing, result, corr);
            case REVERSED -> failAttempt(actor, branchId, processing, result, corr);
        };
    }

    public OrderPaymentSummaryDto cancelPendingAttempt(
            AccessPrincipal actor,
            UUID attemptId,
            CorrelationId corr
    ) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.PAYMENTS_VOID);
        PaymentAttempt attempt = paymentRepo.findAttemptByIdScoped(attemptId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment attempt not found"));
        PaymentStatePolicy.requireTransition(attempt.status(), PaymentAttemptStatus.CANCELLED);

        PaymentTerminalPort.TerminalResult cancelRes = terminalPort.cancelTransaction(
                attempt.idempotencyKey(), attempt.providerTxId(), attempt.terminalRef());
        Instant now = clock.now();

        PaymentAttempt updated = paymentRepo.saveAttempt(new PaymentAttempt(
                attempt.id(), attempt.tenantId(), attempt.branchId(), attempt.orderId(), attempt.paymentMethodId(),
                attempt.cashSessionId(), attempt.initiatedByUserId(), attempt.linkedPaymentId(),
                attempt.idempotencyKey(), attempt.currencyCode(), attempt.requestedAmount(),
                attempt.tenderedAmount(), attempt.changeAmount(), attempt.tipAmount(),
                PaymentAttemptStatus.CANCELLED,
                cancelRes.providerName(), cancelRes.providerTxId(), cancelRes.terminalRef(),
                attempt.maskedPan(), attempt.cardBrand(), attempt.authCode(),
                cancelRes.responseCode(), cancelRes.message(),
                true, now, actor.userId(), attempt.createdAt(), now, attempt.version()
        ));

        paymentRepo.appendTransaction(new PaymentTransactionEntry(
                UUID.randomUUID(), actor.tenantId(), branchId, null, updated.id(),
                "CANCEL", updated.requestedAmount(), cancelRes.providerName(), cancelRes.providerTxId(),
                null, cancelRes.responseCode(), now
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.PAYMENT_CANCELLED,
                "PaymentAttempt", updated.id().toString(), null, corr);
        return buildSummary(actor.tenantId(), branchId, updated.orderId(), updated, null);
    }

    public OrderPaymentSummaryDto finalizeUncertainAttemptFromProvider(
            AccessPrincipal actor,
            PaymentAttempt attempt,
            CorrelationId corr
    ) {
        BranchId branchId = actor.activeBranchId();
        PaymentTerminalPort.TerminalResult queried = statusQueryPort.queryStatus(
                attempt.idempotencyKey(), attempt.providerTxId(), attempt.terminalRef());
        Instant now = clock.now();

        paymentRepo.appendTransaction(new PaymentTransactionEntry(
                UUID.randomUUID(), actor.tenantId(), branchId, null, attempt.id(),
                "QUERY", attempt.requestedAmount(), queried.providerName(), queried.providerTxId(),
                queried.authCode(), queried.responseCode(), now
        ));

        OrderAggregate order = orderRepo.findByIdScoped(attempt.orderId(), actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found in branch"));
        PaymentMethod method = paymentRepo.findPaymentMethodScoped(attempt.paymentMethodId(), actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));

        if (queried.outcome() == PaymentTerminalPort.Outcome.APPROVED) {
            OrderPaymentSummaryDto settled = settleCompletedAttempt(actor, branchId, order, method, attempt,
                    queried.providerTxId(), queried.maskedPan(), queried.cardBrand(),
                    queried.authCode(), queried.responseCode(), queried.providerName(), corr);
            auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.PAYMENT_RECONCILED,
                    "PaymentAttempt", attempt.id().toString(), "{\"outcome\":\"APPROVED\"}", corr);
            return settled;
        }
        return failAttempt(actor, branchId, attempt, queried, corr);
    }

    private OrderPaymentSummaryDto settleCompletedAttempt(
            AccessPrincipal actor,
            BranchId branchId,
            OrderAggregate order,
            PaymentMethod method,
            PaymentAttempt attempt,
            String providerTxId,
            String maskedAccount,
            String cardBrand,
            String authCode,
            String responseCode,
            String providerName,
            CorrelationId corr
    ) {
        Instant now = clock.now();
        PaymentStatePolicy.requireTransition(attempt.status(), PaymentAttemptStatus.COMPLETED);

        UUID paymentId = UUID.randomUUID();
        PaymentRecord settledPayment = paymentRepo.append(new PaymentRecord(
                paymentId, actor.tenantId(), branchId, order.id(), attempt.cashSessionId(), method.id(),
                actor.userId(), attempt.idempotencyKey(), order.currencyCode(),
                attempt.requestedAmount(), attempt.tenderedAmount(), attempt.changeAmount(),
                attempt.tipAmount(), "SETTLED", providerTxId, maskedAccount, now
        ));

        PaymentAttempt completedAttempt = paymentRepo.saveAttempt(new PaymentAttempt(
                attempt.id(), attempt.tenantId(), attempt.branchId(), attempt.orderId(), attempt.paymentMethodId(),
                attempt.cashSessionId(), attempt.initiatedByUserId(), settledPayment.id(),
                attempt.idempotencyKey(), attempt.currencyCode(), attempt.requestedAmount(),
                attempt.tenderedAmount(), attempt.changeAmount(), attempt.tipAmount(),
                PaymentAttemptStatus.COMPLETED, providerName, providerTxId, attempt.terminalRef(),
                maskedAccount, cardBrand, authCode, responseCode, null,
                true, now, actor.userId(), attempt.createdAt(), now, attempt.version()
        ));

        paymentRepo.appendTransaction(new PaymentTransactionEntry(
                UUID.randomUUID(), actor.tenantId(), branchId, settledPayment.id(), completedAttempt.id(),
                method.isCash() ? "SETTLE" : "AUTHORIZE",
                attempt.requestedAmount(), providerName, providerTxId, authCode, responseCode, now
        ));

        if (method.isCash()) {
            cashSessionService.requireAndPostCashTransaction(
                    actor, branchId, attempt.cashSessionId(), CashMovementType.CASH_SALE,
                    attempt.requestedAmount(), "PAY-" + settledPayment.id(),
                    "Cash sale order " + order.orderNumber(), corr
            );
        }

        // Recompute authoritative order paid state
        List<PaymentRecord> allPayments = paymentRepo.findByOrderScoped(order.id(), actor.tenantId(), branchId);
        List<RefundRecord> allRefunds = paymentRepo.findRefundsByOrderScoped(order.id(), actor.tenantId(), branchId);
        BigDecimal totalNetPaid = computeNetPaid(allPayments, allRefunds, order.currencyCode());
        Money remainingAfter = PaymentCalculationPolicy.computeRemainingDue(order.currencyCode(), order.totalAmount(), totalNetPaid);

        boolean fullyPaid = remainingAfter.isZero();
        OrderStatus nextStatus = order.status();
        Instant closedAt = order.closedAt();
        if (fullyPaid && order.status() != OrderStatus.PAID) {
            OrderStatePolicy.requireTransition(order.status(), OrderStatus.PAID);
            nextStatus = OrderStatus.PAID;
            closedAt = now;
        }

        OrderAggregate savedOrder = orderRepo.save(new OrderAggregate(
                order.id(), order.tenantId(), order.branchId(), order.terminalId(), order.diningTableId(),
                order.createdByUserId(), order.assignedWaiterId(), order.orderNumber(), order.orderType(),
                nextStatus, order.currencyCode(), order.subtotalAmount(), order.discountAmount(),
                order.taxAmount(), order.totalAmount(), totalNetPaid, order.guestCount(),
                order.customerReference(), order.notes(), false, order.openedAt(),
                order.sentToKitchenAt(), closedAt, order.version(),
                order.items(), order.discounts(), order.taxes()
        ));

        if (fullyPaid && order.status() != OrderStatus.PAID) {
            orderRepo.recordStatusHistory(UUID.randomUUID(), actor.tenantId(), branchId, savedOrder.id(),
                    order.status(), OrderStatus.PAID, actor.userId(), "Order settled in full", now);
            releaseDiningTableIfOccupied(actor.tenantId(), branchId, savedOrder.diningTableId(), savedOrder.id());
        }

        StringBuilder hardwareNotice = new StringBuilder();
        if (method.opensDrawer()) {
            var drawerRes = cashDrawerPort.openDrawer();
            paymentRepo.appendDrawerEvent(new DrawerEventRecord(
                    UUID.randomUUID(), actor.tenantId(), branchId, actor.terminalId(), attempt.cashSessionId(),
                    order.id(), settledPayment.id(), actor.userId(), null, "CASH_PAYMENT",
                    "Order " + order.orderNumber() + " cash payment",
                    drawerRes.deviceName(), drawerRes.hardwareStatus(), drawerRes.openedOk(),
                    drawerRes.errorMessage(), corr.value(), now
            ));
            if (!drawerRes.openedOk()) {
                hardwareNotice.append("Drawer warning: ").append(drawerRes.errorMessage());
                auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.CASH_DRAWER_FAILED,
                        "CashDrawer", settledPayment.id().toString(), "{\"err\":\"" + drawerRes.errorMessage() + "\"}", corr);
            } else {
                auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.CASH_DRAWER_OPENED,
                        "CashDrawer", settledPayment.id().toString(), "{\"orderId\":\"" + order.id() + "\"}", corr);
            }
        }

        ReceiptDto receiptDto = receiptService.createAndOptionallyPrintOrderReceipt(
                actor, branchId, savedOrder, allPayments, allRefunds, settledPayment.id(), null,
                fullyPaid ? "CUSTOMER" : "PAYMENT_CONFIRMATION", null, corr
        );
        if (!"PRINTED".equals(receiptDto.lastPrintStatus()) && receiptDto.lastPrintError() != null) {
            if (!hardwareNotice.isEmpty()) {
                hardwareNotice.append(" | ");
            }
            hardwareNotice.append("Printer notice: ").append(receiptDto.lastPrintError());
        }

        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.PAYMENT_COMPLETED,
                "Payment", settledPayment.id().toString(),
                "{\"orderId\":\"" + order.id() + "\",\"amount\":" + settledPayment.amount() + "}", corr);
        eventPublisher.publish(actor.tenantId(), branchId, "ORDER_UPDATED", savedOrder.id());

        return buildSummary(actor.tenantId(), branchId, savedOrder.id(), completedAttempt,
                hardwareNotice.isEmpty() ? null : hardwareNotice.toString());
    }

    private OrderPaymentSummaryDto failAttempt(
            AccessPrincipal actor,
            BranchId branchId,
            PaymentAttempt attempt,
            PaymentTerminalPort.TerminalResult result,
            CorrelationId corr
    ) {
        Instant now = clock.now();
        PaymentStatePolicy.requireTransition(attempt.status(), PaymentAttemptStatus.FAILED);

        PaymentAttempt failed = paymentRepo.saveAttempt(new PaymentAttempt(
                attempt.id(), attempt.tenantId(), attempt.branchId(), attempt.orderId(), attempt.paymentMethodId(),
                attempt.cashSessionId(), attempt.initiatedByUserId(), null,
                attempt.idempotencyKey(), attempt.currencyCode(), attempt.requestedAmount(),
                attempt.tenderedAmount(), attempt.changeAmount(), attempt.tipAmount(),
                PaymentAttemptStatus.FAILED, result.providerName(), result.providerTxId(), result.terminalRef(),
                result.maskedPan(), result.cardBrand(), result.authCode(), result.responseCode(), result.message(),
                true, now, actor.userId(), attempt.createdAt(), now, attempt.version()
        ));

        paymentRepo.appendTransaction(new PaymentTransactionEntry(
                UUID.randomUUID(), actor.tenantId(), branchId, null, failed.id(),
                "DECLINE", attempt.requestedAmount(), result.providerName(), result.providerTxId(),
                null, result.responseCode(), now
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.PAYMENT_FAILED,
                "PaymentAttempt", failed.id().toString(), "{\"code\":\"" + result.responseCode() + "\"}", corr);
        return buildSummary(actor.tenantId(), branchId, failed.orderId(), failed, result.message());
    }

    private OrderPaymentSummaryDto cancelAttemptFromGateway(
            AccessPrincipal actor,
            BranchId branchId,
            PaymentAttempt attempt,
            PaymentTerminalPort.TerminalResult result,
            CorrelationId corr
    ) {
        Instant now = clock.now();
        PaymentStatePolicy.requireTransition(attempt.status(), PaymentAttemptStatus.CANCELLED);
        PaymentAttempt cancelled = paymentRepo.saveAttempt(new PaymentAttempt(
                attempt.id(), attempt.tenantId(), attempt.branchId(), attempt.orderId(), attempt.paymentMethodId(),
                attempt.cashSessionId(), attempt.initiatedByUserId(), null,
                attempt.idempotencyKey(), attempt.currencyCode(), attempt.requestedAmount(),
                attempt.tenderedAmount(), attempt.changeAmount(), attempt.tipAmount(),
                PaymentAttemptStatus.CANCELLED, result.providerName(), result.providerTxId(), result.terminalRef(),
                null, null, null, result.responseCode(), result.message(),
                true, now, actor.userId(), attempt.createdAt(), now, attempt.version()
        ));
        paymentRepo.appendTransaction(new PaymentTransactionEntry(
                UUID.randomUUID(), actor.tenantId(), branchId, null, cancelled.id(),
                "CANCEL", attempt.requestedAmount(), result.providerName(), result.providerTxId(),
                null, result.responseCode(), now
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.PAYMENT_CANCELLED,
                "PaymentAttempt", cancelled.id().toString(), null, corr);
        return buildSummary(actor.tenantId(), branchId, cancelled.orderId(), cancelled, result.message());
    }

    private OrderPaymentSummaryDto markUncertainAttempt(
            AccessPrincipal actor,
            BranchId branchId,
            PaymentAttempt attempt,
            PaymentTerminalPort.TerminalResult result,
            CorrelationId corr
    ) {
        Instant now = clock.now();
        PaymentStatePolicy.requireTransition(attempt.status(), PaymentAttemptStatus.UNCERTAIN);
        PaymentAttempt uncertain = paymentRepo.saveAttempt(new PaymentAttempt(
                attempt.id(), attempt.tenantId(), attempt.branchId(), attempt.orderId(), attempt.paymentMethodId(),
                attempt.cashSessionId(), attempt.initiatedByUserId(), null,
                attempt.idempotencyKey(), attempt.currencyCode(), attempt.requestedAmount(),
                attempt.tenderedAmount(), attempt.changeAmount(), attempt.tipAmount(),
                PaymentAttemptStatus.UNCERTAIN, result.providerName(), result.providerTxId(), result.terminalRef(),
                null, null, null, result.responseCode(), result.message(),
                false, null, null, attempt.createdAt(), now, attempt.version()
        ));
        paymentRepo.appendTransaction(new PaymentTransactionEntry(
                UUID.randomUUID(), actor.tenantId(), branchId, null, uncertain.id(),
                "TIMEOUT", attempt.requestedAmount(), result.providerName(), result.providerTxId(),
                null, result.responseCode(), now
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.PAYMENT_FAILED,
                "PaymentAttempt", uncertain.id().toString(), "{\"uncertain\":true}", corr);
        return buildSummary(actor.tenantId(), branchId, uncertain.orderId(), uncertain,
                "Card terminal timed out. Reconcile / query terminal status before retrying.");
    }

    private void releaseDiningTableIfOccupied(TenantId tenantId, BranchId branchId, UUID tableId, UUID orderId) {
        if (tableId == null) {
            return;
        }
        tableRepo.findTableScoped(tableId, tenantId, branchId).ifPresent(table -> {
            if (orderId.equals(table.activeOrderId())) {
                tableRepo.saveTable(new DiningTable(
                        table.id(), table.tenantId(), table.branchId(), table.diningAreaId(), table.diningAreaName(),
                        table.tableCode(), table.displayName(), table.capacity(), table.sortOrder(),
                        TableStatus.CLEANING, null, table.active(), table.version()
                ));
                eventPublisher.publish(tenantId, branchId, "TABLE_RELEASED", table.id());
            }
        });
    }

    public OrderPaymentSummaryDto buildSummary(
            TenantId tenantId,
            BranchId branchId,
            UUID orderId,
            PaymentAttempt latestAttempt,
            String hardwareNotice
    ) {
        OrderAggregate order = orderRepo.findByIdScoped(orderId, tenantId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found in branch"));
        Map<UUID, PaymentMethod> methods = paymentRepo.findPaymentMethodsByTenant(tenantId).stream()
                .collect(Collectors.toMap(PaymentMethod::id, Function.identity()));

        List<PaymentRecord> payments = paymentRepo.findByOrderScoped(orderId, tenantId, branchId);
        List<RefundRecord> refunds = paymentRepo.findRefundsByOrderScoped(orderId, tenantId, branchId);
        List<PaymentAttempt> attempts = paymentRepo.findAttemptsByOrderScoped(orderId, tenantId, branchId);

        Map<UUID, BigDecimal> refundedByPayment = refunds.stream()
                .filter(r -> "COMPLETED".equals(r.status()))
                .collect(Collectors.groupingBy(
                        RefundRecord::originalPaymentId,
                        Collectors.reducing(BigDecimal.ZERO, RefundRecord::amount, BigDecimal::add)
                ));

        BigDecimal totalCompleted = payments.stream()
                .filter(p -> "SETTLED".equals(p.status()) || "CAPTURED".equals(p.status()))
                .map(PaymentRecord::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRefunded = refunds.stream()
                .filter(r -> "COMPLETED".equals(r.status()))
                .map(RefundRecord::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netPaid = Money.of(totalCompleted, order.currencyCode())
                .subtract(Money.of(totalRefunded, order.currencyCode())).amount();
        BigDecimal remaining = PaymentCalculationPolicy.computeRemainingDue(
                order.currencyCode(), order.totalAmount(), netPaid).amount();

        List<PaymentDto> paymentDtos = payments.stream()
                .map(p -> {
                    PaymentMethod pm = methods.get(p.paymentMethodId());
                    return new PaymentDto(
                            p.id(), p.orderId(), p.cashSessionId(), p.paymentMethodId(),
                            pm != null ? pm.code() : "UNKNOWN",
                            pm != null ? pm.methodType() : "UNKNOWN",
                            p.processedByUserId(), p.idempotencyKey(), p.currencyCode(),
                            p.amount(), p.tenderedAmount(), p.changeAmount(), p.tipAmount(),
                            refundedByPayment.getOrDefault(p.id(), BigDecimal.ZERO),
                            p.status(), p.externalReference(), p.maskedAccount(), p.paidAt()
                    );
                })
                .toList();

        List<PaymentAttemptDto> attemptDtos = attempts.stream().map(PaymentService::toAttemptDto).toList();
        List<RefundDto> refundDtos = refunds.stream().map(RefundService::toRefundDto).toList();
        PaymentAttemptDto latestDto = latestAttempt != null
                ? toAttemptDto(latestAttempt)
                : (attemptDtos.isEmpty() ? null : attemptDtos.getLast());

        return new OrderPaymentSummaryDto(
                order.id(), order.orderNumber(), order.status().name(), order.currencyCode(),
                order.totalAmount(), netPaid, totalRefunded, remaining,
                paymentDtos, attemptDtos, refundDtos, latestDto, null, hardwareNotice
        );
    }

    private List<PaymentMethod> seedDefaultMethods(TenantId tenantId) {
        return List.of(
                paymentRepo.savePaymentMethod(new PaymentMethod(UUID.randomUUID(), tenantId, "CASH", "Cash", "CASH", true, true, 0L)),
                paymentRepo.savePaymentMethod(new PaymentMethod(UUID.randomUUID(), tenantId, "CARD", "Card Terminal", "CARD", false, true, 0L)),
                paymentRepo.savePaymentMethod(new PaymentMethod(UUID.randomUUID(), tenantId, "WALLET", "Digital Wallet", "DIGITAL_WALLET", false, true, 0L)),
                paymentRepo.savePaymentMethod(new PaymentMethod(UUID.randomUUID(), tenantId, "VOUCHER", "Gift Voucher", "VOUCHER", false, true, 0L))
        );
    }

    private static BigDecimal computeNetPaid(List<PaymentRecord> payments, List<RefundRecord> refunds, String currencyCode) {
        Money paid = Money.zero(currencyCode);
        for (PaymentRecord p : payments) {
            if ("SETTLED".equals(p.status()) || "CAPTURED".equals(p.status())) {
                paid = paid.add(Money.of(p.amount(), currencyCode));
            }
        }
        for (RefundRecord r : refunds) {
            if ("COMPLETED".equals(r.status())) {
                paid = paid.subtract(Money.of(r.amount(), currencyCode));
            }
        }
        return paid.isNegative() ? BigDecimal.ZERO : paid.amount();
    }

    private static PaymentAttempt withStatus(PaymentAttempt a, PaymentAttemptStatus next, Instant now) {
        return new PaymentAttempt(
                a.id(), a.tenantId(), a.branchId(), a.orderId(), a.paymentMethodId(), a.cashSessionId(),
                a.initiatedByUserId(), a.linkedPaymentId(), a.idempotencyKey(), a.currencyCode(),
                a.requestedAmount(), a.tenderedAmount(), a.changeAmount(), a.tipAmount(),
                next, a.providerName(), a.providerTxId(), a.terminalRef(),
                a.maskedPan(), a.cardBrand(), a.authCode(), a.responseCode(), a.failureReason(),
                a.reconciled(), a.reconciledAt(), a.reconciledByUserId(),
                a.createdAt(), now, a.version()
        );
    }

    public static PaymentAttemptDto toAttemptDto(PaymentAttempt a) {
        return new PaymentAttemptDto(
                a.id(), a.orderId(), a.paymentMethodId(), a.cashSessionId(), a.linkedPaymentId(),
                a.idempotencyKey(), a.currencyCode(), a.requestedAmount(), a.tenderedAmount(),
                a.changeAmount(), a.tipAmount(), a.status().name(), a.providerName(), a.providerTxId(),
                a.terminalRef(), a.maskedPan(), a.cardBrand(), a.authCode(), a.responseCode(),
                a.failureReason(), a.reconciled(), a.createdAt(), a.updatedAt(), a.version()
        );
    }

    private static BranchId requireBranchPermission(AccessPrincipal actor, PermissionCode permission) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(permission, "Active branch session is required");
        }
        PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), permission);
        return actor.activeBranchId();
    }

    private static BranchId requireProcessPermission(AccessPrincipal actor) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(PermissionCode.PAYMENTS_PROCESS, "Active branch session is required");
        }
        if (actor.hasPermission(PermissionCode.PAYMENTS_PROCESS)) {
            PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), PermissionCode.PAYMENTS_PROCESS);
        } else {
            PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), PermissionCode.PAYMENTS_CREATE);
        }
        return actor.activeBranchId();
    }
}
