package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cash_sessions")
public class CashSessionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "terminal_id", nullable = false, updatable = false)
    private UUID terminalId;

    @Column(name = "opened_by_user_id", nullable = false, updatable = false)
    private UUID openedByUserId;

    @Column(name = "closed_by_user_id")
    private UUID closedByUserId;

    @Column(name = "reopened_by_user_id")
    private UUID reopenedByUserId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, updatable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currencyCode;

    @Column(name = "opening_float", nullable = false, precision = 14, scale = 4)
    private BigDecimal openingFloat;

    @Column(name = "expected_cash_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal expectedCashAmount;

    @Column(name = "counted_cash_amount", precision = 14, scale = 4)
    private BigDecimal countedCashAmount;

    @Column(name = "variance_amount", precision = 14, scale = 4)
    private BigDecimal varianceAmount;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "reopened_at")
    private Instant reopenedAt;

    @Column(name = "reopen_reason", length = 300)
    private String reopenReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CashSessionEntity() {
    }

    public CashSessionEntity(
            UUID id, UUID tenantId, UUID branchId, UUID terminalId, UUID openedByUserId,
            String currencyCode, BigDecimal openingFloat, BigDecimal expectedCashAmount,
            String status, Instant openedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.terminalId = terminalId;
        this.openedByUserId = openedByUserId;
        this.currencyCode = currencyCode;
        this.openingFloat = openingFloat;
        this.expectedCashAmount = expectedCashAmount;
        this.status = status;
        this.openedAt = openedAt;
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
    public UUID getTerminalId() { return terminalId; }
    public UUID getOpenedByUserId() { return openedByUserId; }
    public UUID getClosedByUserId() { return closedByUserId; }
    public void setClosedByUserId(UUID closedByUserId) { this.closedByUserId = closedByUserId; }
    public UUID getReopenedByUserId() { return reopenedByUserId; }
    public void setReopenedByUserId(UUID reopenedByUserId) { this.reopenedByUserId = reopenedByUserId; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getOpeningFloat() { return openingFloat; }
    public BigDecimal getExpectedCashAmount() { return expectedCashAmount; }
    public void setExpectedCashAmount(BigDecimal expectedCashAmount) { this.expectedCashAmount = expectedCashAmount; }
    public BigDecimal getCountedCashAmount() { return countedCashAmount; }
    public void setCountedCashAmount(BigDecimal countedCashAmount) { this.countedCashAmount = countedCashAmount; }
    public BigDecimal getVarianceAmount() { return varianceAmount; }
    public void setVarianceAmount(BigDecimal varianceAmount) { this.varianceAmount = varianceAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public Instant getReopenedAt() { return reopenedAt; }
    public void setReopenedAt(Instant reopenedAt) { this.reopenedAt = reopenedAt; }
    public String getReopenReason() { return reopenReason; }
    public void setReopenReason(String reopenReason) { this.reopenReason = reopenReason; }
    public long getVersion() { return version; }
}
