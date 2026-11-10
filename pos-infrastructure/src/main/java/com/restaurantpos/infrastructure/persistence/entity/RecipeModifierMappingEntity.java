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
@Table(name = "recipe_modifier_mappings")
public class RecipeModifierMappingEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "recipe_version_id", nullable = false, updatable = false)
    private UUID recipeVersionId;

    @Column(name = "modifier_id", nullable = false)
    private UUID modifierId;

    @Column(name = "action_type", nullable = false, length = 16)
    private String actionType;

    @Column(name = "ingredient_id")
    private UUID ingredientId;

    @Column(name = "sub_recipe_id")
    private UUID subRecipeId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "quantity_delta", nullable = false, precision = 14, scale = 6)
    private BigDecimal quantityDelta = BigDecimal.ZERO;

    @Column(name = "scale_factor", precision = 10, scale = 4)
    private BigDecimal scaleFactor;

    @Column(name = "notes", length = 300)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecipeModifierMappingEntity() {
    }

    public RecipeModifierMappingEntity(
            UUID id, UUID tenantId, UUID recipeVersionId, UUID modifierId, String actionType,
            UUID ingredientId, UUID subRecipeId, UUID unitId, BigDecimal quantityDelta,
            BigDecimal scaleFactor, String notes
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.recipeVersionId = recipeVersionId;
        this.modifierId = modifierId;
        this.actionType = actionType;
        this.ingredientId = ingredientId;
        this.subRecipeId = subRecipeId;
        this.unitId = unitId;
        this.quantityDelta = quantityDelta;
        this.scaleFactor = scaleFactor;
        this.notes = notes;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRecipeVersionId() { return recipeVersionId; }
    public UUID getModifierId() { return modifierId; }
    public String getActionType() { return actionType; }
    public UUID getIngredientId() { return ingredientId; }
    public UUID getSubRecipeId() { return subRecipeId; }
    public UUID getUnitId() { return unitId; }
    public BigDecimal getQuantityDelta() { return quantityDelta; }
    public BigDecimal getScaleFactor() { return scaleFactor; }
    public String getNotes() { return notes; }
}
