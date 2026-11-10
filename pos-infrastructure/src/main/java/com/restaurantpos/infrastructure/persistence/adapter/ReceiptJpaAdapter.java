package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.port.ReceiptRepositoryPort;
import com.restaurantpos.domain.receipt.ReceiptRecord;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.ReceiptEntity;
import com.restaurantpos.infrastructure.persistence.repository.ReceiptJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class ReceiptJpaAdapter implements ReceiptRepositoryPort {

    private final ReceiptJpaRepository receiptRepo;
    private final DatabaseExceptionTranslator translator;

    public ReceiptJpaAdapter(ReceiptJpaRepository receiptRepo, DatabaseExceptionTranslator translator) {
        this.receiptRepo = receiptRepo;
        this.translator = translator;
    }

    @Override
    @Transactional
    public ReceiptRecord save(ReceiptRecord r) {
        ReceiptEntity entity = receiptRepo.findByIdAndTenantIdAndBranchId(
                        r.id(), r.tenantId().value(), r.branchId().value())
                .map(existing -> {
                    existing.setRenderedText(r.renderedText());
                    existing.setPrintCount(r.printCount());
                    existing.setLastPrintStatus(r.lastPrintStatus());
                    existing.setLastPrintError(r.lastPrintError());
                    existing.setLastPrintedAt(r.lastPrintedAt());
                    return existing;
                })
                .orElseGet(() -> new ReceiptEntity(
                        r.id(), r.tenantId().value(), r.branchId().value(),
                        r.orderId(), r.paymentId(), r.refundId(), r.cashSessionId(),
                        r.receiptNumber(), r.receiptType(), r.language(), r.paperWidth(),
                        r.encoding(), r.renderedText(), r.escPosBase64(),
                        r.printCount(), r.lastPrintStatus(), r.lastPrintError(),
                        r.lastPrintedAt(), r.createdByUserId(), r.createdAt()
                ));
        ReceiptEntity saved = translator.execute("save receipt", () -> receiptRepo.saveAndFlush(entity));
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReceiptRecord> findByIdScoped(UUID receiptId, TenantId tenantId, BranchId branchId) {
        return receiptRepo.findByIdAndTenantIdAndBranchId(receiptId, tenantId.value(), branchId.value())
                .map(ReceiptJpaAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReceiptRecord> findByOrderScoped(UUID orderId, TenantId tenantId, BranchId branchId) {
        return receiptRepo.findByOrderIdAndTenantIdAndBranchIdOrderByCreatedAtDesc(
                        orderId, tenantId.value(), branchId.value()).stream()
                .map(ReceiptJpaAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReceiptRecord> findByCashSessionScoped(UUID cashSessionId, TenantId tenantId, BranchId branchId) {
        return receiptRepo.findFirstByCashSessionIdAndTenantIdAndBranchIdOrderByCreatedAtDesc(
                        cashSessionId, tenantId.value(), branchId.value())
                .map(ReceiptJpaAdapter::toDomain);
    }

    private static ReceiptRecord toDomain(ReceiptEntity e) {
        return new ReceiptRecord(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getOrderId(), e.getPaymentId(), e.getRefundId(), e.getCashSessionId(),
                e.getReceiptNumber(), e.getReceiptType(), e.getLanguage(), e.getPaperWidth(),
                e.getEncoding(), e.getRenderedText(), e.getEscPosBase64(), e.getPrintCount(),
                e.getLastPrintStatus(), e.getLastPrintError(), e.getLastPrintedAt(),
                e.getCreatedByUserId(), e.getCreatedAt()
        );
    }
}
