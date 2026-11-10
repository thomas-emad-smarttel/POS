package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
public class StockTransferEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "transfer_number", nullable = false, length = 64)
    private String transferNumber;

    @Column(name = "source_branch_id", nullable = false)
    private UUID sourceBranchId;

    @Column(name = "source_location_id", nullable = false)
    private UUID sourceLocationId;

    @Column(name = "destination_branch_id", nullable = false)
    private UUID destinationBranchId;

    @Column(name = "destination_location_id", nullable = false)
    private UUID destinationLocationId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "dispatched_by_user_id")
    private UUID dispatchedByUserId;

    @Column(name = "received_by_user_id")
    private UUID receivedByUserId;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StockTransferEntity() {
    }

    public StockTransferEntity(UUID id, UUID tenantId, String transferNumber) {
        this.id = id;
        this.tenantId = tenantId;
        this.transferNumber = transferNumber;
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
    public String getTransferNumber() { return transferNumber; }
    public UUID getSourceBranchId() { return sourceBranchId; }
    public UUID getSourceLocationId() { return sourceLocationId; }
    public UUID getDestinationBranchId() { return destinationBranchId; }
    public UUID getDestinationLocationId() { return destinationLocationId; }
    public String getStatus() { return status; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public UUID getDispatchedByUserId() { return dispatchedByUserId; }
    public UUID getReceivedByUserId() { return receivedByUserId; }
    public String getNotes() { return notes; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public long getVersion() { return version; }

    public void setSourceBranchId(UUID sourceBranchId) { this.sourceBranchId = sourceBranchId; }
    public void setSourceLocationId(UUID sourceLocationId) { this.sourceLocationId = sourceLocationId; }
    public void setDestinationBranchId(UUID destinationBranchId) { this.destinationBranchId = destinationBranchId; }
    public void setDestinationLocationId(UUID destinationLocationId) { this.destinationLocationId = destinationLocationId; }
    public void setStatus(String status) { this.status = status; }
    public void setRequestedByUserId(UUID requestedByUserId) { this.requestedByUserId = requestedByUserId; }
    public void setApprovedByUserId(UUID approvedByUserId) { this.approvedByUserId = approvedByUserId; }
    public void setDispatchedByUserId(UUID dispatchedByUserId) { this.dispatchedByUserId = dispatchedByUserId; }
    public void setReceivedByUserId(UUID receivedByUserId) { this.receivedByUserId = receivedByUserId; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public void setDispatchedAt(Instant dispatchedAt) { this.dispatchedAt = dispatchedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
}
