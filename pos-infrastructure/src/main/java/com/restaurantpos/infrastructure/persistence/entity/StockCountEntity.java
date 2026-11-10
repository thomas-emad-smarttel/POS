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
@Table(name = "stock_counts")
public class StockCountEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "count_number", nullable = false, length = 64)
    private String countNumber;

    @Column(name = "stock_location_id", nullable = false)
    private UUID stockLocationId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "submitted_by_user_id")
    private UUID submittedByUserId;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "posted_by_user_id")
    private UUID postedByUserId;

    @Column(name = "approval_id")
    private UUID approvalId;

    @Column(name = "total_variance_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal totalVarianceCost = BigDecimal.ZERO;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StockCountEntity() {
    }

    public StockCountEntity(UUID id, UUID tenantId, UUID branchId, String countNumber, UUID stockLocationId, UUID categoryId, UUID createdByUserId) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.countNumber = countNumber;
        this.stockLocationId = stockLocationId;
        this.categoryId = categoryId;
        this.createdByUserId = createdByUserId;
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
    public String getCountNumber() { return countNumber; }
    public UUID getStockLocationId() { return stockLocationId; }
    public UUID getCategoryId() { return categoryId; }
    public String getStatus() { return status; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public UUID getSubmittedByUserId() { return submittedByUserId; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public UUID getPostedByUserId() { return postedByUserId; }
    public UUID getApprovalId() { return approvalId; }
    public BigDecimal getTotalVarianceCost() { return totalVarianceCost; }
    public String getNotes() { return notes; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getPostedAt() { return postedAt; }
    public long getVersion() { return version; }

    public void setStatus(String status) { this.status = status; }
    public void setSubmittedByUserId(UUID submittedByUserId) { this.submittedByUserId = submittedByUserId; }
    public void setApprovedByUserId(UUID approvedByUserId) { this.approvedByUserId = approvedByUserId; }
    public void setPostedByUserId(UUID postedByUserId) { this.postedByUserId = postedByUserId; }
    public void setApprovalId(UUID approvalId) { this.approvalId = approvalId; }
    public void setTotalVarianceCost(BigDecimal totalVarianceCost) { this.totalVarianceCost = totalVarianceCost; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
}
