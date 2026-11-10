package com.restaurantpos.domain.port;

import com.restaurantpos.domain.receipt.ReceiptRecord;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepositoryPort {

    ReceiptRecord save(ReceiptRecord receipt);

    Optional<ReceiptRecord> findByIdScoped(UUID receiptId, TenantId tenantId, BranchId branchId);

    List<ReceiptRecord> findByOrderScoped(UUID orderId, TenantId tenantId, BranchId branchId);

    Optional<ReceiptRecord> findByCashSessionScoped(UUID cashSessionId, TenantId tenantId, BranchId branchId);
}
