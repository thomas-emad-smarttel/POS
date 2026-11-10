package com.restaurantpos.application.payment;

import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.domain.cash.CashSession;
import com.restaurantpos.domain.exception.ResourceNotFoundException;
import com.restaurantpos.domain.exception.UnauthorizedOperationException;
import com.restaurantpos.domain.payment.PaymentAttempt;
import com.restaurantpos.domain.port.CashSessionRepositoryPort;
import com.restaurantpos.domain.port.PaymentRepositoryPort;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.dto.payment.CashSessionDto;
import com.restaurantpos.shared.dto.payment.OrderPaymentSummaryDto;
import com.restaurantpos.shared.dto.payment.PaymentAttemptDto;
import com.restaurantpos.shared.dto.payment.ReconciliationReportDto;
import com.restaurantpos.shared.dto.payment.RefundDto;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.CorrelationId;
import com.restaurantpos.shared.security.PermissionCode;

import java.util.List;
import java.util.UUID;

/**
 * Identifies uncertain/pending external transactions, failed refunds, and cash session variances,
 * and queries provider status before retrying or finalizing uncertain card charges.
 */
public class FinancialReconciliationService {

    private final PaymentRepositoryPort paymentRepo;
    private final CashSessionRepositoryPort cashSessionRepo;
    private final PaymentService paymentService;

    public FinancialReconciliationService(
            PaymentRepositoryPort paymentRepo,
            CashSessionRepositoryPort cashSessionRepo,
            PaymentService paymentService
    ) {
        this.paymentRepo = paymentRepo;
        this.cashSessionRepo = cashSessionRepo;
        this.paymentService = paymentService;
    }

    public ReconciliationReportDto getOverview(AccessPrincipal actor) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.REPORTS_PAYMENTS_VIEW);
        List<PaymentAttemptDto> pendingOrUncertain = paymentRepo.findPendingOrUncertainAttempts(actor.tenantId(), branchId)
                .stream()
                .map(PaymentService::toAttemptDto)
                .toList();
        List<RefundDto> failedRefunds = paymentRepo.findFailedRefunds(actor.tenantId(), branchId)
                .stream()
                .map(RefundService::toRefundDto)
                .toList();
        List<CashSessionDto> variances = cashSessionRepo.findVarianceSessions(actor.tenantId(), branchId).stream()
                .map((CashSession s) -> CashSessionService.buildDerivedDto(
                        s, cashSessionRepo.findMovementsBySession(s.id(), actor.tenantId())))
                .toList();
        return new ReconciliationReportDto(pendingOrUncertain, List.of(), failedRefunds, variances);
    }

    public OrderPaymentSummaryDto queryAndReconcileAttempt(AccessPrincipal actor, UUID attemptId, CorrelationId corr) {
        BranchId branchId = requireBranchPermission(actor, PermissionCode.CASH_SESSION_RECONCILE);
        PaymentAttempt attempt = paymentRepo.findAttemptByIdScoped(attemptId, actor.tenantId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment attempt not found in branch"));
        if (attempt.status().name().equals("COMPLETED") && attempt.reconciled()) {
            return paymentService.buildSummary(actor.tenantId(), branchId, attempt.orderId(), attempt, null);
        }
        return paymentService.finalizeUncertainAttemptFromProvider(actor, attempt, corr);
    }

    private static BranchId requireBranchPermission(AccessPrincipal actor, PermissionCode permission) {
        if (actor == null || actor.activeBranchId() == null) {
            throw new UnauthorizedOperationException(permission, "Active branch session is required");
        }
        PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), permission);
        return actor.activeBranchId();
    }
}
