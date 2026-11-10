package com.restaurantpos.application.payment;

import com.restaurantpos.application.audit.AuditRecordingService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.cash.CashMovement;
import com.restaurantpos.domain.cash.CashSession;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.order.OrderAggregate;
import com.restaurantpos.domain.order.OrderItemDetail;
import com.restaurantpos.domain.payment.PaymentMethod;
import com.restaurantpos.domain.payment.PaymentRecord;
import com.restaurantpos.domain.payment.RefundRecord;
import com.restaurantpos.domain.port.BranchRepositoryPort;
import com.restaurantpos.domain.port.PaymentRepositoryPort;
import com.restaurantpos.domain.port.PosOrderRepositoryPort;
import com.restaurantpos.domain.port.ReceiptPrinterPort;
import com.restaurantpos.domain.port.ReceiptRepositoryPort;
import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.domain.port.TenantRepositoryPort;
import com.restaurantpos.domain.port.UserRepositoryPort;
import com.restaurantpos.domain.receipt.ReceiptRecord;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.domain.security.UserAccount;
import com.restaurantpos.domain.tenancy.Branch;
import com.restaurantpos.domain.tenancy.Tenant;
import com.restaurantpos.shared.dto.payment.GenerateReceiptRequest;
import com.restaurantpos.shared.dto.payment.ReceiptDto;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.security.AuditAction;
import com.restaurantpos.shared.security.PermissionCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generates, persists, formats (ESC/POS EN/AR/Bilingual), prints, and reprints receipts.
 * Printing failures are recorded on the receipt entity and never roll back financial transactions.
 */
public class ReceiptService {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final ReceiptRepositoryPort receiptRepo;
    private final ReceiptPrinterPort printerPort;
    private final PosOrderRepositoryPort orderRepo;
    private final PaymentRepositoryPort paymentRepo;
    private final TenantRepositoryPort tenantRepo;
    private final BranchRepositoryPort branchRepo;
    private final UserRepositoryPort userRepo;
    private final AuditRecordingService auditService;
    private final SystemClockPort clock;
    private final PaymentPolicyConfig policy;

    public ReceiptService(
            ReceiptRepositoryPort receiptRepo,
            ReceiptPrinterPort printerPort,
            PosOrderRepositoryPort orderRepo,
            PaymentRepositoryPort paymentRepo,
            TenantRepositoryPort tenantRepo,
            BranchRepositoryPort branchRepo,
            UserRepositoryPort userRepo,
            AuditRecordingService auditService,
            SystemClockPort clock,
            PaymentPolicyConfig policy
    ) {
        this.receiptRepo = receiptRepo;
        this.printerPort = printerPort;
        this.orderRepo = orderRepo;
        this.paymentRepo = paymentRepo;
        this.tenantRepo = tenantRepo;
        this.branchRepo = branchRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
        this.clock = clock;
        this.policy = policy;
    }

    public ReceiptDto generateForOrder(
            AccessPrincipal actor,
            UUID orderId,
            UUID paymentId,
            UUID refundId,
            String receiptType,
            GenerateReceiptRequest req,
            CorrelationId corr
    ) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.PAYMENTS_VIEW);
        OrderAggregate order = orderRepo.findByIdScoped(orderId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found in branch"));
        List<PaymentRecord> payments = paymentRepo.findByOrderScoped(orderId, actor.tenantId(), branchId);
        List<RefundRecord> refunds = paymentRepo.findRefundsByOrderScoped(orderId, actor.tenantId(), branchId);
        return createAndOptionallyPrintOrderReceipt(
                actor, branchId, order, payments, refunds, paymentId, refundId, receiptType, req, corr
        );
    }

    public ReceiptDto createAndOptionallyPrintOrderReceipt(
            AccessPrincipal actor,
            BranchId branchId,
            OrderAggregate order,
            List<PaymentRecord> payments,
            List<RefundRecord> refunds,
            UUID paymentId,
            UUID refundId,
            String receiptType,
            GenerateReceiptRequest req,
            CorrelationId corr
    ) {
        Tenant tenant = tenantRepo.findById(actor.tenantId()).orElse(null);
        Branch branch = branchRepo.findByIdAndTenant(branchId, actor.tenantId()).orElse(null);
        UserAccount cashier = userRepo.findByIdAndTenant(actor.userId(), actor.tenantId()).orElse(null);
        Map<UUID, PaymentMethod> methodsById = paymentRepo.findPaymentMethodsByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(PaymentMethod::id, Function.identity()));

        String language = req != null && req.language() != null ? req.language() : policy.defaultReceiptLanguage();
        int paperWidth = req != null && req.paperWidth() != null ? req.paperWidth() : policy.defaultReceiptPaperWidth();
        String encoding = req != null && req.encoding() != null ? req.encoding() : policy.defaultReceiptEncoding();
        boolean printNow = req != null ? req.printImmediately() : policy.autoPrintOnPayment();

        Instant now = clock.now();
        String receiptNum = "RCP-" + Long.toString(now.toEpochMilli(), 36).toUpperCase()
                + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        List<ReceiptPrinterPort.LineItem> itemLines = new ArrayList<>();
        for (OrderItemDetail item : order.items()) {
            if (item.voided()) {
                continue;
            }
            List<String> mods = item.modifiers().stream()
                    .map(m -> m.modifierNameSnapshot() + " (" + m.unitPriceSnapshot().setScale(2, RoundingMode.HALF_UP) + ")")
                    .toList();
            String qty = item.quantity().stripTrailingZeros().toPlainString();
            String label = item.variantNameSnapshot() != null
                    ? item.productNameSnapshot() + " [" + item.variantNameSnapshot() + "]"
                    : item.productNameSnapshot();
            itemLines.add(new ReceiptPrinterPort.LineItem(
                    label, qty, item.lineTotal().setScale(2, RoundingMode.HALF_UP).toPlainString(), mods
            ));
        }

        List<ReceiptPrinterPort.PaymentLine> payLines = new ArrayList<>();
        BigDecimal totalChange = BigDecimal.ZERO;
        BigDecimal netPaid = BigDecimal.ZERO;
        for (PaymentRecord p : payments) {
            if ("SETTLED".equals(p.status()) || "CAPTURED".equals(p.status())) {
                PaymentMethod pm = methodsById.get(p.paymentMethodId());
                String methodLabel = pm != null ? pm.name() : "Payment";
                payLines.add(new ReceiptPrinterPort.PaymentLine(
                        methodLabel,
                        p.amount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        p.maskedAccount() != null ? p.maskedAccount() : p.externalReference()
                ));
                totalChange = totalChange.add(p.changeAmount());
                netPaid = netPaid.add(p.amount());
            }
        }
        for (RefundRecord r : refunds) {
            if ("COMPLETED".equals(r.status())) {
                payLines.add(new ReceiptPrinterPort.PaymentLine(
                        "REFUND",
                        "-" + r.amount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        r.externalReference()
                ));
                netPaid = netPaid.subtract(r.amount());
            }
        }
        BigDecimal remaining = order.totalAmount().subtract(netPaid).max(BigDecimal.ZERO);

        var printReq = new ReceiptPrinterPort.PrintPayloadRequest(
                tenant != null ? tenant.displayName() : "Restaurant POS",
                branch != null ? branch.name() : "Branch",
                null,
                null,
                receiptNum,
                receiptType,
                language,
                paperWidth,
                encoding,
                "DUPLICATE".equalsIgnoreCase(receiptType),
                order.orderNumber(),
                TS_FMT.format(now),
                cashier != null ? cashier.fullName() : actor.username(),
                order.currencyCode(),
                itemLines,
                order.subtotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                order.discountAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                order.taxAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                order.totalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                payLines,
                netPaid.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                totalChange.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                remaining.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "Thank you for dining with us / شكراً لزيارتكم"
        );

        ReceiptPrinterPort.RenderedAndPrintResult pr = printerPort.renderAndOptionallyPrint(printReq, printNow);
        boolean printed = "PRINTED".equals(pr.printStatus());

        ReceiptRecord saved = receiptRepo.save(new ReceiptRecord(
                UUID.randomUUID(),
                actor.tenantId(),
                branchId,
                order.id(),
                paymentId,
                refundId,
                null,
                receiptNum,
                receiptType,
                language,
                paperWidth,
                encoding,
                pr.renderedText(),
                pr.escPosBase64(),
                printed ? 1 : 0,
                pr.printStatus(),
                pr.errorMessage(),
                printed ? now : null,
                actor.userId(),
                now
        ));

        auditService.record(actor.tenantId(), branchId, actor.userId(),
                printed ? AuditAction.RECEIPT_PRINTED : AuditAction.RECEIPT_GENERATED,
                "Receipt", saved.id().toString(),
                "{\"receiptNumber\":\"" + receiptNum + "\",\"status\":\"" + saved.lastPrintStatus() + "\"}", corr);
        return toDto(saved);
    }

    public ReceiptDto generateCashSessionClosingReceipt(
            AccessPrincipal actor,
            BranchId branchId,
            CashSession session,
            List<CashMovement> movements,
            CorrelationId corr
    ) {
        Tenant tenant = tenantRepo.findById(actor.tenantId()).orElse(null);
        Branch branch = branchRepo.findByIdAndTenant(branchId, actor.tenantId()).orElse(null);
        UserAccount cashier = userRepo.findByIdAndTenant(actor.userId(), actor.tenantId()).orElse(null);
        Instant now = clock.now();
        String receiptNum = "CSR-" + Long.toString(now.toEpochMilli(), 36).toUpperCase();

        List<ReceiptPrinterPort.LineItem> lines = movements.stream()
                .map(m -> new ReceiptPrinterPort.LineItem(
                        m.movementType().name() + ": " + m.reason(),
                        "1",
                        m.amount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        List.of()
                ))
                .toList();

        var printReq = new ReceiptPrinterPort.PrintPayloadRequest(
                tenant != null ? tenant.displayName() : "Restaurant POS",
                branch != null ? branch.name() : "Branch",
                null, null,
                receiptNum,
                "CASH_SESSION_REPORT",
                policy.defaultReceiptLanguage(),
                policy.defaultReceiptPaperWidth(),
                policy.defaultReceiptEncoding(),
                false,
                "SESSION-" + session.id().toString().substring(0, 8),
                TS_FMT.format(now),
                cashier != null ? cashier.fullName() : actor.username(),
                session.currencyCode(),
                lines,
                session.openingFloat().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "0.00",
                "0.00",
                session.expectedCashAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                List.of(new ReceiptPrinterPort.PaymentLine(
                        "Counted Cash",
                        (session.countedCashAmount() != null ? session.countedCashAmount() : BigDecimal.ZERO)
                                .setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        "Variance: " + (session.varianceAmount() != null ? session.varianceAmount() : BigDecimal.ZERO)
                                .setScale(2, RoundingMode.HALF_UP)
                )),
                (session.countedCashAmount() != null ? session.countedCashAmount() : BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "0.00",
                (session.varianceAmount() != null ? session.varianceAmount() : BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "End of Shift Cash Report"
        );

        var pr = printerPort.renderAndOptionallyPrint(printReq, policy.autoPrintOnPayment());
        boolean printed = "PRINTED".equals(pr.printStatus());
        ReceiptRecord saved = receiptRepo.save(new ReceiptRecord(
                UUID.randomUUID(),
                actor.tenantId(),
                branchId,
                null, null, null, session.id(),
                receiptNum,
                "CASH_SESSION_REPORT",
                policy.defaultReceiptLanguage(),
                policy.defaultReceiptPaperWidth(),
                policy.defaultReceiptEncoding(),
                pr.renderedText(),
                pr.escPosBase64(),
                printed ? 1 : 0,
                pr.printStatus(),
                pr.errorMessage(),
                printed ? now : null,
                actor.userId(),
                now
        ));
        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.RECEIPT_GENERATED,
                "CashSession", session.id().toString(), "{\"receiptNumber\":\"" + receiptNum + "\"}", corr);
        return toDto(saved);
    }

    public ReceiptDto reprint(AccessPrincipal actor, UUID receiptId, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.PAYMENTS_VIEW);
        ReceiptRecord existing = receiptRepo.findByIdScoped(receiptId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found in branch"));

        var res = printerPort.reprintRawEscPos(existing.escPosBase64());
        boolean printed = "PRINTED".equals(res.printStatus());
        Instant now = clock.now();
        String duplicateText = existing.renderedText().startsWith("*** DUPLICATE")
                ? existing.renderedText()
                : "*** DUPLICATE COPY ***\n" + existing.renderedText();

        ReceiptRecord updated = receiptRepo.save(new ReceiptRecord(
                existing.id(),
                existing.tenantId(),
                existing.branchId(),
                existing.orderId(),
                existing.paymentId(),
                existing.refundId(),
                existing.cashSessionId(),
                existing.receiptNumber(),
                existing.receiptType(),
                existing.language(),
                existing.paperWidth(),
                existing.encoding(),
                duplicateText,
                existing.escPosBase64(),
                printed ? existing.printCount() + 1 : existing.printCount(),
                res.printStatus(),
                res.errorMessage(),
                printed ? now : existing.lastPrintedAt(),
                existing.createdByUserId(),
                existing.createdAt()
        ));

        auditService.record(actor.tenantId(), branchId, actor.userId(), AuditAction.RECEIPT_REPRINTED,
                "Receipt", updated.id().toString(),
                "{\"printStatus\":\"" + res.printStatus() + "\",\"printCount\":" + updated.printCount() + "}", corr);
        return toDto(updated);
    }

    public List<ReceiptDto> listForOrder(AccessPrincipal actor, UUID orderId) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.PAYMENTS_VIEW);
        return receiptRepo.findByOrderScoped(orderId, actor.tenantId(), branchId).stream()
                .map(ReceiptService::toDto)
                .toList();
    }

    public ReceiptDto getReceipt(AccessPrincipal actor, UUID receiptId) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.PAYMENTS_VIEW);
        ReceiptRecord rec = receiptRepo.findByIdScoped(receiptId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found in branch"));
        return toDto(rec);
    }

    public static ReceiptDto toDto(ReceiptRecord r) {
        return new ReceiptDto(
                r.id(), r.branchId().value(), r.orderId(), r.paymentId(), r.refundId(), r.cashSessionId(),
                r.receiptNumber(), r.receiptType(), r.language(), r.paperWidth(), r.encoding(),
                r.renderedText(), r.printCount(), r.lastPrintStatus(), r.lastPrintError(),
                r.lastPrintedAt(), r.createdAt()
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
