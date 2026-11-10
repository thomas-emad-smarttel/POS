package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.payment.DrawerEventRecord;
import com.restaurantpos.domain.payment.PaymentAttempt;
import com.restaurantpos.domain.payment.PaymentAttemptStatus;
import com.restaurantpos.domain.payment.PaymentMethod;
import com.restaurantpos.domain.payment.PaymentRecord;
import com.restaurantpos.domain.payment.PaymentTransactionEntry;
import com.restaurantpos.domain.payment.RefundRecord;
import com.restaurantpos.domain.port.PaymentRepositoryPort;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.DrawerEventEntity;
import com.restaurantpos.infrastructure.persistence.entity.PaymentAttemptEntity;
import com.restaurantpos.infrastructure.persistence.entity.PaymentEntity;
import com.restaurantpos.infrastructure.persistence.entity.PaymentMethodEntity;
import com.restaurantpos.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.restaurantpos.infrastructure.persistence.entity.RefundEntity;
import com.restaurantpos.infrastructure.persistence.repository.DrawerEventJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.PaymentAttemptJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.PaymentJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.PaymentMethodJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.PaymentTransactionJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.RefundJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentJpaAdapter implements PaymentRepositoryPort {

    private final PaymentMethodJpaRepository methodRepo;
    private final PaymentJpaRepository paymentRepo;
    private final PaymentAttemptJpaRepository attemptRepo;
    private final PaymentTransactionJpaRepository txRepo;
    private final RefundJpaRepository refundRepo;
    private final DrawerEventJpaRepository drawerRepo;
    private final DatabaseExceptionTranslator translator;

    public PaymentJpaAdapter(
            PaymentMethodJpaRepository methodRepo,
            PaymentJpaRepository paymentRepo,
            PaymentAttemptJpaRepository attemptRepo,
            PaymentTransactionJpaRepository txRepo,
            RefundJpaRepository refundRepo,
            DrawerEventJpaRepository drawerRepo,
            DatabaseExceptionTranslator translator
    ) {
        this.methodRepo = methodRepo;
        this.paymentRepo = paymentRepo;
        this.attemptRepo = attemptRepo;
        this.txRepo = txRepo;
        this.refundRepo = refundRepo;
        this.drawerRepo = drawerRepo;
        this.translator = translator;
    }

    @Override
    @Transactional
    public PaymentMethod savePaymentMethod(PaymentMethod method) {
        PaymentMethodEntity entity = methodRepo.findByIdAndTenantId(method.id(), method.tenantId().value())
                .map(existing -> {
                    existing.setName(method.name());
                    existing.setMethodType(method.methodType());
                    existing.setOpensDrawer(method.opensDrawer());
                    existing.setActive(method.active());
                    return existing;
                })
                .orElseGet(() -> new PaymentMethodEntity(
                        method.id(), method.tenantId().value(), method.code(),
                        method.name(), method.methodType(), method.opensDrawer()
                ));
        entity.setActive(method.active());
        PaymentMethodEntity saved = translator.execute("save payment method", () -> methodRepo.saveAndFlush(entity));
        return toMethodDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethod> findPaymentMethodsByTenant(TenantId tenantId) {
        return methodRepo.findByTenantIdOrderByNameAsc(tenantId.value()).stream()
                .map(PaymentJpaAdapter::toMethodDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentMethod> findPaymentMethodScoped(UUID paymentMethodId, TenantId tenantId) {
        return methodRepo.findByIdAndTenantId(paymentMethodId, tenantId.value())
                .map(PaymentJpaAdapter::toMethodDomain);
    }

    @Override
    @Transactional
    public PaymentAttempt saveAttempt(PaymentAttempt attempt) {
        PaymentAttemptEntity entity = attemptRepo.findByIdAndTenantIdAndBranchId(
                        attempt.id(), attempt.tenantId().value(), attempt.branchId().value())
                .map(existing -> {
                    existing.setLinkedPaymentId(attempt.linkedPaymentId());
                    existing.setStatus(attempt.status().name());
                    existing.setProviderName(attempt.providerName());
                    existing.setProviderTxId(attempt.providerTxId());
                    existing.setTerminalRef(attempt.terminalRef());
                    existing.setMaskedPan(attempt.maskedPan());
                    existing.setCardBrand(attempt.cardBrand());
                    existing.setAuthCode(attempt.authCode());
                    existing.setResponseCode(attempt.responseCode());
                    existing.setFailureReason(attempt.failureReason());
                    existing.setReconciled(attempt.reconciled());
                    existing.setReconciledAt(attempt.reconciledAt());
                    existing.setReconciledByUserId(attempt.reconciledByUserId());
                    return existing;
                })
                .orElseGet(() -> {
                    PaymentAttemptEntity created = new PaymentAttemptEntity(
                            attempt.id(), attempt.tenantId().value(), attempt.branchId().value(),
                            attempt.orderId(), attempt.paymentMethodId(), attempt.cashSessionId(),
                            attempt.initiatedByUserId(), attempt.idempotencyKey(), attempt.currencyCode(),
                            attempt.requestedAmount(), attempt.tenderedAmount(), attempt.changeAmount(),
                            attempt.tipAmount(), attempt.status().name(), attempt.providerName(),
                            attempt.createdAt()
                    );
                    created.setLinkedPaymentId(attempt.linkedPaymentId());
                    created.setProviderTxId(attempt.providerTxId());
                    created.setTerminalRef(attempt.terminalRef());
                    created.setMaskedPan(attempt.maskedPan());
                    created.setCardBrand(attempt.cardBrand());
                    created.setAuthCode(attempt.authCode());
                    created.setResponseCode(attempt.responseCode());
                    created.setFailureReason(attempt.failureReason());
                    created.setReconciled(attempt.reconciled());
                    created.setReconciledAt(attempt.reconciledAt());
                    created.setReconciledByUserId(attempt.reconciledByUserId());
                    return created;
                });
        PaymentAttemptEntity saved = translator.execute("save payment attempt", () -> attemptRepo.saveAndFlush(entity));
        return toAttemptDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentAttempt> findAttemptByIdScoped(UUID attemptId, TenantId tenantId, BranchId branchId) {
        return attemptRepo.findByIdAndTenantIdAndBranchId(attemptId, tenantId.value(), branchId.value())
                .map(PaymentJpaAdapter::toAttemptDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentAttempt> findAttemptByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey) {
        return attemptRepo.findByTenantIdAndBranchIdAndIdempotencyKey(tenantId.value(), branchId.value(), idempotencyKey)
                .map(PaymentJpaAdapter::toAttemptDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentAttempt> findAttemptsByOrderScoped(UUID orderId, TenantId tenantId, BranchId branchId) {
        return attemptRepo.findByOrderIdAndTenantIdAndBranchIdOrderByCreatedAtAsc(
                        orderId, tenantId.value(), branchId.value())
                .stream()
                .map(PaymentJpaAdapter::toAttemptDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentAttempt> findPendingOrUncertainAttempts(TenantId tenantId, BranchId branchId) {
        return attemptRepo.findByTenantIdAndBranchIdAndStatusInOrderByCreatedAtAsc(
                        tenantId.value(), branchId.value(), List.of("PENDING", "PROCESSING", "UNCERTAIN"))
                .stream()
                .map(PaymentJpaAdapter::toAttemptDomain)
                .toList();
    }

    @Override
    @Transactional
    public PaymentRecord append(PaymentRecord payment) {
        PaymentEntity entity = new PaymentEntity(
                payment.id(), payment.tenantId().value(), payment.branchId().value(),
                payment.orderId(), payment.cashSessionId(), payment.paymentMethodId(),
                payment.processedByUserId(), payment.idempotencyKey(), payment.currencyCode(),
                payment.amount(), payment.tenderedAmount(), payment.changeAmount(),
                payment.tipAmount(), payment.status(), payment.externalReference(),
                payment.maskedAccount(), payment.paidAt()
        );
        PaymentEntity saved = translator.execute("append payment", () -> paymentRepo.saveAndFlush(entity));
        return toPaymentDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentRecord> findPaymentByIdScoped(UUID paymentId, TenantId tenantId, BranchId branchId) {
        return paymentRepo.findByIdAndTenantIdAndBranchId(paymentId, tenantId.value(), branchId.value())
                .map(PaymentJpaAdapter::toPaymentDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentRecord> findPaymentByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey) {
        return paymentRepo.findByTenantIdAndBranchIdAndIdempotencyKey(tenantId.value(), branchId.value(), idempotencyKey)
                .map(PaymentJpaAdapter::toPaymentDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRecord> findByOrderScoped(UUID orderId, TenantId tenantId, BranchId branchId) {
        return paymentRepo.findByOrderIdAndTenantIdAndBranchIdOrderByPaidAtAsc(orderId, tenantId.value(), branchId.value())
                .stream()
                .map(PaymentJpaAdapter::toPaymentDomain)
                .toList();
    }

    @Override
    @Transactional
    public PaymentTransactionEntry appendTransaction(PaymentTransactionEntry tx) {
        PaymentTransactionEntity saved = translator.execute("append payment tx", () -> txRepo.saveAndFlush(
                new PaymentTransactionEntity(
                        tx.id(), tx.tenantId().value(), tx.branchId().value(),
                        tx.paymentId(), tx.paymentAttemptId(), tx.transactionType(),
                        tx.amount(), tx.gatewayName(), tx.gatewayTxRef(),
                        tx.authCode(), tx.responseCode(), tx.occurredAt()
                )));
        return new PaymentTransactionEntry(
                saved.getId(), TenantId.of(saved.getTenantId()), BranchId.of(saved.getBranchId()),
                saved.getPaymentId(), saved.getPaymentAttemptId(), saved.getTransactionType(),
                saved.getAmount(), saved.getGatewayName(), saved.getGatewayTxRef(),
                saved.getAuthCode(), saved.getResponseCode(), saved.getOccurredAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransactionEntry> findTransactionsByAttemptScoped(UUID attemptId, TenantId tenantId, BranchId branchId) {
        return txRepo.findByPaymentAttemptIdAndTenantIdAndBranchIdOrderByOccurredAtAsc(
                        attemptId, tenantId.value(), branchId.value()).stream()
                .map(e -> new PaymentTransactionEntry(
                        e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                        e.getPaymentId(), e.getPaymentAttemptId(), e.getTransactionType(),
                        e.getAmount(), e.getGatewayName(), e.getGatewayTxRef(),
                        e.getAuthCode(), e.getResponseCode(), e.getOccurredAt()
                ))
                .toList();
    }

    @Override
    @Transactional
    public RefundRecord appendRefund(RefundRecord refund) {
        RefundEntity saved = translator.execute("append refund", () -> refundRepo.saveAndFlush(new RefundEntity(
                refund.id(), refund.tenantId().value(), refund.branchId().value(),
                refund.orderId(), refund.originalPaymentId(), refund.issuedByUserId(),
                refund.approvedByUserId(), refund.idempotencyKey(), refund.currencyCode(),
                refund.amount(), refund.status(), refund.reason(), refund.failureReason(),
                refund.externalReference(), refund.refundedAt()
        )));
        return toRefundDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundRecord> findRefundByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey) {
        return refundRepo.findByTenantIdAndBranchIdAndIdempotencyKey(tenantId.value(), branchId.value(), idempotencyKey)
                .map(PaymentJpaAdapter::toRefundDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundRecord> findRefundsByOrderScoped(UUID orderId, TenantId tenantId, BranchId branchId) {
        return refundRepo.findByOrderIdAndTenantIdAndBranchIdOrderByRefundedAtAsc(orderId, tenantId.value(), branchId.value())
                .stream()
                .map(PaymentJpaAdapter::toRefundDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundRecord> findRefundsByPaymentScoped(UUID paymentId, TenantId tenantId, BranchId branchId) {
        return refundRepo.findByOriginalPaymentIdAndTenantIdAndBranchIdOrderByRefundedAtAsc(
                        paymentId, tenantId.value(), branchId.value())
                .stream()
                .map(PaymentJpaAdapter::toRefundDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundRecord> findFailedRefunds(TenantId tenantId, BranchId branchId) {
        return refundRepo.findByTenantIdAndBranchIdAndStatusOrderByRefundedAtDesc(tenantId.value(), branchId.value(), "FAILED")
                .stream()
                .map(PaymentJpaAdapter::toRefundDomain)
                .toList();
    }

    @Override
    @Transactional
    public DrawerEventRecord appendDrawerEvent(DrawerEventRecord event) {
        DrawerEventEntity saved = translator.execute("append drawer event", () -> drawerRepo.saveAndFlush(
                new DrawerEventEntity(
                        event.id(), event.tenantId().value(), event.branchId().value(),
                        event.terminalId(), event.cashSessionId(), event.orderId(), event.paymentId(),
                        event.actorUserId(), event.approvalId(), event.triggerType(), event.reason(),
                        event.hardwareDeviceName(), event.hardwareStatus(), event.openedOk(),
                        event.errorMessage(), event.correlationId(), event.occurredAt()
                )));
        return toDrawerDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DrawerEventRecord> findDrawerEventsByBranch(TenantId tenantId, BranchId branchId, int page, int size) {
        return drawerRepo.findByTenantIdAndBranchIdOrderByOccurredAtDesc(
                        tenantId.value(), branchId.value(), PageRequest.of(page, size))
                .stream()
                .map(PaymentJpaAdapter::toDrawerDomain)
                .toList();
    }

    private static PaymentMethod toMethodDomain(PaymentMethodEntity e) {
        return new PaymentMethod(
                e.getId(), TenantId.of(e.getTenantId()), e.getCode(), e.getName(),
                e.getMethodType(), e.isOpensDrawer(), e.isActive(), e.getVersion()
        );
    }

    private static PaymentAttempt toAttemptDomain(PaymentAttemptEntity e) {
        return new PaymentAttempt(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getOrderId(), e.getPaymentMethodId(), e.getCashSessionId(),
                e.getInitiatedByUserId(), e.getLinkedPaymentId(), e.getIdempotencyKey(),
                e.getCurrencyCode(), e.getRequestedAmount(), e.getTenderedAmount(),
                e.getChangeAmount(), e.getTipAmount(), PaymentAttemptStatus.valueOf(e.getStatus()),
                e.getProviderName(), e.getProviderTxId(), e.getTerminalRef(),
                e.getMaskedPan(), e.getCardBrand(), e.getAuthCode(), e.getResponseCode(),
                e.getFailureReason(), e.isReconciled(), e.getReconciledAt(),
                e.getReconciledByUserId(), e.getCreatedAt(), e.getUpdatedAt(), e.getVersion()
        );
    }

    private static PaymentRecord toPaymentDomain(PaymentEntity e) {
        return new PaymentRecord(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getOrderId(), e.getCashSessionId(), e.getPaymentMethodId(),
                e.getProcessedByUserId(), e.getIdempotencyKey(), e.getCurrencyCode(),
                e.getAmount(), e.getTenderedAmount(), e.getChangeAmount(), e.getTipAmount(),
                e.getStatus(), e.getExternalReference(), e.getMaskedAccount(), e.getPaidAt()
        );
    }

    private static RefundRecord toRefundDomain(RefundEntity e) {
        return new RefundRecord(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getOrderId(), e.getOriginalPaymentId(), e.getIssuedByUserId(),
                e.getApprovedByUserId(), e.getIdempotencyKey(), e.getCurrencyCode(),
                e.getAmount(), e.getStatus(), e.getReason(), e.getFailureReason(),
                e.getExternalReference(), e.getRefundedAt()
        );
    }

    private static DrawerEventRecord toDrawerDomain(DrawerEventEntity e) {
        return new DrawerEventRecord(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getTerminalId(), e.getCashSessionId(), e.getOrderId(), e.getPaymentId(),
                e.getActorUserId(), e.getApprovalId(), e.getTriggerType(), e.getReason(),
                e.getHardwareDeviceName(), e.getHardwareStatus(), e.isOpenedOk(),
                e.getErrorMessage(), e.getCorrelationId(), e.getOccurredAt()
        );
    }
}
