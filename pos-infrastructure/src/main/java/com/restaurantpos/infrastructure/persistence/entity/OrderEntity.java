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
@Table(name = "orders")
public class OrderEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "terminal_id")
    private UUID terminalId;

    @Column(name = "dining_table_id")
    private UUID diningTableId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "assigned_waiter_id")
    private UUID assignedWaiterId;

    @Column(name = "order_number", nullable = false, length = 64)
    private String orderNumber;

    @Column(name = "order_type", nullable = false, length = 32)
    private String orderType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currencyCode;

    @Column(name = "subtotal_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal paidAmount;

    @Column(name = "guest_count", nullable = false)
    private int guestCount;

    @Column(name = "customer_reference", length = 200)
    private String customerReference;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "held", nullable = false)
    private boolean held;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "sent_to_kitchen_at")
    private Instant sentToKitchenAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected OrderEntity() {
    }

    public OrderEntity(
            UUID id,
            UUID tenantId,
            UUID branchId,
            UUID createdByUserId,
            String orderNumber,
            String orderType,
            String status,
            String currencyCode,
            BigDecimal subtotalAmount,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            int guestCount,
            Instant openedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.createdByUserId = createdByUserId;
        this.orderNumber = orderNumber;
        this.orderType = orderType;
        this.status = status;
        this.currencyCode = currencyCode;
        this.subtotalAmount = subtotalAmount;
        this.discountAmount = discountAmount;
        this.taxAmount = taxAmount;
        this.totalAmount = totalAmount;
        this.paidAmount = paidAmount;
        this.guestCount = guestCount;
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
    public UUID getDiningTableId() { return diningTableId; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public UUID getAssignedWaiterId() { return assignedWaiterId; }
    public String getOrderNumber() { return orderNumber; }
    public String getOrderType() { return orderType; }
    public String getStatus() { return status; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public int getGuestCount() { return guestCount; }
    public String getCustomerReference() { return customerReference; }
    public String getNotes() { return notes; }
    public boolean isHeld() { return held; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getSentToKitchenAt() { return sentToKitchenAt; }
    public Instant getClosedAt() { return closedAt; }
    public long getVersion() { return version; }

    public void setTerminalId(UUID terminalId) { this.terminalId = terminalId; }
    public void setDiningTableId(UUID diningTableId) { this.diningTableId = diningTableId; }
    public void setAssignedWaiterId(UUID assignedWaiterId) { this.assignedWaiterId = assignedWaiterId; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public void setStatus(String status) { this.status = status; }
    public void setSubtotalAmount(BigDecimal subtotalAmount) { this.subtotalAmount = subtotalAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public void setGuestCount(int guestCount) { this.guestCount = guestCount; }
    public void setCustomerReference(String customerReference) { this.customerReference = customerReference; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setHeld(boolean held) { this.held = held; }
    public void setSentToKitchenAt(Instant sentToKitchenAt) { this.sentToKitchenAt = sentToKitchenAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public void setVersion(long version) { this.version = version; }
}
