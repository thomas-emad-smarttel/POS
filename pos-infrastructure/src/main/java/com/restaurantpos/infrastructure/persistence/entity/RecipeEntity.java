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
@Table(name = "recipes")
public class RecipeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "product_variant_id")
    private UUID productVariantId;

    @Column(name = "recipe_name", nullable = false, length = 160)
    private String recipeName;

    @Column(name = "sub_recipe", nullable = false)
    private boolean subRecipe;

    @Column(name = "active_version_id")
    private UUID activeVersionId;

    @Column(name = "validated", nullable = false)
    private boolean validated;

    @Column(name = "yield_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal yieldQuantity = BigDecimal.ONE;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RecipeEntity() {
    }

    public RecipeEntity(UUID id, UUID tenantId) {
        this.id = id;
        this.tenantId = tenantId;
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
    public UUID getProductId() { return productId; }
    public UUID getProductVariantId() { return productVariantId; }
    public String getRecipeName() { return recipeName; }
    public boolean isSubRecipe() { return subRecipe; }
    public UUID getActiveVersionId() { return activeVersionId; }
    public boolean isValidated() { return validated; }
    public boolean isActive() { return active; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public void setProductVariantId(UUID productVariantId) { this.productVariantId = productVariantId; }
    public void setRecipeName(String recipeName) { this.recipeName = recipeName; }
    public void setSubRecipe(boolean subRecipe) { this.subRecipe = subRecipe; }
    public void setActiveVersionId(UUID activeVersionId) { this.activeVersionId = activeVersionId; }
    public void setValidated(boolean validated) { this.validated = validated; }
    public void setYieldQuantity(BigDecimal yieldQuantity) { this.yieldQuantity = yieldQuantity; }
    public void setActive(boolean active) { this.active = active; }
}
