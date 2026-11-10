package com.restaurantpos.domain.port;

import com.restaurantpos.domain.payment.DrawerEventRecord;
import com.restaurantpos.domain.payment.PaymentAttempt;
import com.restaurantpos.domain.payment.PaymentMethod;
import com.restaurantpos.domain.payment.PaymentRecord;
import com.restaurantpos.domain.payment.PaymentTransactionEntry;
import com.restaurantpos.domain.payment.RefundRecord;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound financial persistence port for payment methods, payment attempts,
 * append-only payment records, transaction ledger entries, refunds, and drawer events.
 */
public interface PaymentRepositoryPort {

    PaymentMethod savePaymentMethod(PaymentMethod method);

    List<PaymentMethod> findPaymentMethodsByTenant(TenantId tenantId);

    Optional<PaymentMethod> findPaymentMethodScoped(UUID paymentMethodId, TenantId tenantId);

    PaymentAttempt saveAttempt(PaymentAttempt attempt);

    Optional<PaymentAttempt> findAttemptByIdScoped(UUID attemptId, TenantId tenantId, BranchId branchId);

    Optional<PaymentAttempt> findAttemptByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey);

    List<PaymentAttempt> findAttemptsByOrderScoped(UUID orderId, TenantId tenantId, BranchId branchId);

    List<PaymentAttempt> findPendingOrUncertainAttempts(TenantId tenantId, BranchId branchId);

    PaymentRecord append(PaymentRecord payment);

    Optional<PaymentRecord> findPaymentByIdScoped(UUID paymentId, TenantId tenantId, BranchId branchId);

    Optional<PaymentRecord> findPaymentByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey);

    List<PaymentRecord> findByOrderScoped(UUID orderId, TenantId tenantId, BranchId branchId);

    PaymentTransactionEntry appendTransaction(PaymentTransactionEntry tx);

    List<PaymentTransactionEntry> findTransactionsByAttemptScoped(UUID attemptId, TenantId tenantId, BranchId branchId);

    RefundRecord appendRefund(RefundRecord refund);

    Optional<RefundRecord> findRefundByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey);

    List<RefundRecord> findRefundsByOrderScoped(UUID orderId, TenantId tenantId, BranchId branchId);

    List<RefundRecord> findRefundsByPaymentScoped(UUID paymentId, TenantId tenantId, BranchId branchId);

    List<RefundRecord> findFailedRefunds(TenantId tenantId, BranchId branchId);

    DrawerEventRecord appendDrawerEvent(DrawerEventRecord event);

    List<DrawerEventRecord> findDrawerEventsByBranch(TenantId tenantId, BranchId branchId, int page, int size);
}
