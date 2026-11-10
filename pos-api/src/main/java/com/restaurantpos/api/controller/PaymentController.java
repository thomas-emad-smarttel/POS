package com.restaurantpos.api.controller;

import com.restaurantpos.api.security.SecurityPrincipalResolver;
import com.restaurantpos.application.payment.FinancialReconciliationService;
import com.restaurantpos.application.payment.PaymentService;
import com.restaurantpos.application.payment.RefundService;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.payment.OrderPaymentSummaryDto;
import com.restaurantpos.shared.dto.payment.PaymentMethodDto;
import com.restaurantpos.shared.dto.payment.ProcessPaymentRequest;
import com.restaurantpos.shared.dto.payment.ReconciliationReportDto;
import com.restaurantpos.shared.dto.payment.RefundDto;
import com.restaurantpos.shared.dto.payment.RefundRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for authoritative payment processing, split/partial tenders,
 * idempotent refunds/reversals, and uncertain transaction reconciliation.
 */
@RestController
@RequestMapping("/api/v1/payments")
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;
    private final FinancialReconciliationService reconciliationService;

    public PaymentController(
            PaymentService paymentService,
            RefundService refundService,
            FinancialReconciliationService reconciliationService
    ) {
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/methods")
    public ApiResponse<List<PaymentMethodDto>> listMethods() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(paymentService.listPaymentMethods(actor), corr.value());
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<OrderPaymentSummaryDto> getOrderSummary(@PathVariable UUID orderId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(paymentService.getOrderPaymentSummary(actor, orderId), corr.value());
    }

    @PostMapping("/process")
    public ApiResponse<OrderPaymentSummaryDto> processPayment(@Valid @RequestBody ProcessPaymentRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(paymentService.processPayment(actor, request, corr), corr.value());
    }

    @PostMapping("/attempts/{attemptId}/cancel")
    public ApiResponse<OrderPaymentSummaryDto> cancelAttempt(@PathVariable UUID attemptId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(paymentService.cancelPendingAttempt(actor, attemptId, corr), corr.value());
    }

    @PostMapping("/refunds")
    public ApiResponse<OrderPaymentSummaryDto> issueRefund(@Valid @RequestBody RefundRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(refundService.issueRefund(actor, request, corr), corr.value());
    }

    @GetMapping("/orders/{orderId}/refunds")
    public ApiResponse<List<RefundDto>> listRefundsForOrder(@PathVariable UUID orderId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(refundService.listRefundsForOrder(actor, orderId), corr.value());
    }

    @GetMapping("/reconciliation")
    public ApiResponse<ReconciliationReportDto> getReconciliationOverview() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(reconciliationService.getOverview(actor), corr.value());
    }

    @PostMapping("/attempts/{attemptId}/reconcile")
    public ApiResponse<OrderPaymentSummaryDto> reconcileAttempt(@PathVariable UUID attemptId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(reconciliationService.queryAndReconcileAttempt(actor, attemptId, corr), corr.value());
    }
}
