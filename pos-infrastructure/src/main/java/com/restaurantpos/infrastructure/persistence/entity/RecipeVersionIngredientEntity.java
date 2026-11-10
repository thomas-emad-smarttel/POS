package com.restaurantpos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recipe_version_ingredients")
public class RecipeVersionIngredientEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "recipe_version_id", nullable = false, updatable = false)
    private UUID recipeVersionId;

    @Column(name = "ingredient_id")
    private UUID ingredientId;

    @Column(name = "sub_recipe_id")
    private UUID subRecipeId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 6)
    private BigDecimal quantity;

    @Column(name = "waste_loss_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal wasteLossPct = BigDecimal.ZERO;

    @Column(name = "optional_flag", nullable = false)
    private boolean optionalFlag;

    @Column(name = "alternative_group", length = 64)
    private String alternativeGroup;

    @Column(name = "notes", length = 300)
    private String notes;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecipeVersionIngredientEntity() {
    }

    public RecipeVersionIngredientEntity(
            UUID id, UUID tenantId, UUID recipeVersionId, UUID ingredientId, UUID subRecipeId,
            UUID unitId, BigDecimal quantity, BigDecimal wasteLossPct, boolean optionalFlag,
            String alternativeGroup, String notes, int displayOrder
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.recipeVersionId = recipeVersionId;
        this.ingredientId = ingredientId;
        this.subRecipeId = subRecipeId;
        this.unitId = unitId;
        this.quantity = quantity;
        this.wasteLossPct = wasteLossPct;
        this.optionalFlag = optionalFlag;
        this.alternativeGroup = alternativeGroup;
        this.notes = notes;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRecipeVersionId() { return recipeVersionId; }
    public UUID getIngredientId() { return ingredientId; }
    public UUID getSubRecipeId() { return subRecipeId; }
    public UUID getUnitId() { return unitId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getWasteLossPct() { return wasteLossPct; }
    public boolean isOptionalFlag() { return optionalFlag; }
    public String getAlternativeGroup() { return alternativeGroup; }
    public String getNotes() { return notes; }
    public int getDisplayOrder() { return displayOrder; }
}
