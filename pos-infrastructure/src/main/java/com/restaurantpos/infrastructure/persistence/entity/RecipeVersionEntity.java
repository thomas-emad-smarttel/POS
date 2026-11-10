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
@Table(name = "recipe_versions")
public class RecipeVersionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "recipe_id", nullable = false, updatable = false)
    private UUID recipeId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "yield_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal yieldQuantity;

    @Column(name = "yield_unit_id")
    private UUID yieldUnitId;

    @Column(name = "prep_loss_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal prepLossPct = BigDecimal.ZERO;

    @Column(name = "prep_instructions", length = 2000)
    private String prepInstructions;

    @Column(name = "expected_prep_minutes", nullable = false)
    private int expectedPrepMinutes;

    @Column(name = "theoretical_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal theoreticalCost = BigDecimal.ZERO;

    @Column(name = "validated", nullable = false)
    private boolean validated;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RecipeVersionEntity() {
    }

    public RecipeVersionEntity(UUID id, UUID tenantId, UUID recipeId, int versionNumber) {
        this.id = id;
        this.tenantId = tenantId;
        this.recipeId = recipeId;
        this.versionNumber = versionNumber;
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
    public UUID getRecipeId() { return recipeId; }
    public int getVersionNumber() { return versionNumber; }
    public String getStatus() { return status; }
    public BigDecimal getYieldQuantity() { return yieldQuantity; }
    public UUID getYieldUnitId() { return yieldUnitId; }
    public BigDecimal getPrepLossPct() { return prepLossPct; }
    public String getPrepInstructions() { return prepInstructions; }
    public int getExpectedPrepMinutes() { return expectedPrepMinutes; }
    public BigDecimal getTheoreticalCost() { return theoreticalCost; }
    public boolean isValidated() { return validated; }
    public Instant getPublishedAt() { return publishedAt; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public long getVersion() { return version; }

    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public void setStatus(String status) { this.status = status; }
    public void setYieldQuantity(BigDecimal yieldQuantity) { this.yieldQuantity = yieldQuantity; }
    public void setYieldUnitId(UUID yieldUnitId) { this.yieldUnitId = yieldUnitId; }
    public void setPrepLossPct(BigDecimal prepLossPct) { this.prepLossPct = prepLossPct; }
    public void setPrepInstructions(String prepInstructions) { this.prepInstructions = prepInstructions; }
    public void setExpectedPrepMinutes(int expectedPrepMinutes) { this.expectedPrepMinutes = expectedPrepMinutes; }
    public void setTheoreticalCost(BigDecimal theoreticalCost) { this.theoreticalCost = theoreticalCost; }
    public void setValidated(boolean validated) { this.validated = validated; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }
}
