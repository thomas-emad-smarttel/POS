package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_policies")
public class InventoryPolicyEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "commitment_mode", nullable = false, length = 48)
    private String commitmentMode = "RESERVE_ON_SEND_CONSUME_ON_PREP";

    @Column(name = "allow_negative_stock", nullable = false)
    private boolean allowNegativeStock;

    @Column(name = "low_stock_alert_enabled", nullable = false)
    private boolean lowStockAlertEnabled = true;

    @Column(name = "expiry_warning_days", nullable = false)
    private int expiryWarningDays = 7;

    @Column(name = "high_waste_threshold_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal highWasteThresholdQty = new BigDecimal("50.0000");

    @Column(name = "waste_approval_threshold_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal wasteApprovalThresholdAmount = new BigDecimal("100.0000");

    @Column(name = "count_variance_approval_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal countVarianceApprovalPct = new BigDecimal("5.0000");

    @Column(name = "transfer_approval_required", nullable = false)
    private boolean transferApprovalRequired = true;

    @Column(name = "allow_manual_consumption_reverse", nullable = false)
    private boolean allowManualConsumptionReverse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected InventoryPolicyEntity() {
    }

    public InventoryPolicyEntity(UUID id, UUID tenantId, UUID branchId) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public String getCommitmentMode() { return commitmentMode; }
    public boolean isAllowNegativeStock() { return allowNegativeStock; }
    public boolean isLowStockAlertEnabled() { return lowStockAlertEnabled; }
    public int getExpiryWarningDays() { return expiryWarningDays; }
    public BigDecimal getHighWasteThresholdQty() { return highWasteThresholdQty; }
    public BigDecimal getWasteApprovalThresholdAmount() { return wasteApprovalThresholdAmount; }
    public BigDecimal getCountVarianceApprovalPct() { return countVarianceApprovalPct; }
    public boolean isTransferApprovalRequired() { return transferApprovalRequired; }
    public boolean isAllowManualConsumptionReverse() { return allowManualConsumptionReverse; }
    public long getVersion() { return version; }

    public void setCommitmentMode(String commitmentMode) { this.commitmentMode = commitmentMode; }
    public void setAllowNegativeStock(boolean allowNegativeStock) { this.allowNegativeStock = allowNegativeStock; }
    public void setLowStockAlertEnabled(boolean lowStockAlertEnabled) { this.lowStockAlertEnabled = lowStockAlertEnabled; }
    public void setExpiryWarningDays(int expiryWarningDays) { this.expiryWarningDays = expiryWarningDays; }
    public void setHighWasteThresholdQty(BigDecimal highWasteThresholdQty) { this.highWasteThresholdQty = highWasteThresholdQty; }
    public void setWasteApprovalThresholdAmount(BigDecimal wasteApprovalThresholdAmount) { this.wasteApprovalThresholdAmount = wasteApprovalThresholdAmount; }
    public void setCountVarianceApprovalPct(BigDecimal countVarianceApprovalPct) { this.countVarianceApprovalPct = countVarianceApprovalPct; }
    public void setTransferApprovalRequired(boolean transferApprovalRequired) { this.transferApprovalRequired = transferApprovalRequired; }
    public void setAllowManualConsumptionReverse(boolean allowManualConsumptionReverse) { this.allowManualConsumptionReverse = allowManualConsumptionReverse; }
}
