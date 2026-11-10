package com.restaurantpos.domain.port;

import com.restaurantpos.domain.inventory.InventoryAlert;
import com.restaurantpos.domain.inventory.InventoryBalance;
import com.restaurantpos.domain.inventory.InventoryCategory;
import com.restaurantpos.domain.inventory.InventoryItem;
import com.restaurantpos.domain.inventory.InventoryLocation;
import com.restaurantpos.domain.inventory.InventoryPolicy;
import com.restaurantpos.domain.inventory.Recipe;
import com.restaurantpos.domain.inventory.StockCount;
import com.restaurantpos.domain.inventory.StockMovement;
import com.restaurantpos.domain.inventory.StockReservation;
import com.restaurantpos.domain.inventory.StockTransfer;
import com.restaurantpos.domain.inventory.StockWasteRecord;
import com.restaurantpos.domain.inventory.UnitConversion;
import com.restaurantpos.domain.inventory.UnitOfMeasure;
import com.restaurantpos.domain.kitchen.OutboxWorkItem;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepositoryPort {

    UnitOfMeasure saveUnit(UnitOfMeasure unit);

    Optional<UnitOfMeasure> findUnitScoped(UUID unitId, TenantId tenantId);

    List<UnitOfMeasure> findUnitsByTenant(TenantId tenantId);

    UnitConversion saveUnitConversion(UnitConversion conversion);

    List<UnitConversion> findUnitConversionsByTenant(TenantId tenantId);

    InventoryCategory saveCategory(InventoryCategory category);

    Optional<InventoryCategory> findCategoryScoped(UUID categoryId, TenantId tenantId);

    List<InventoryCategory> findCategoriesByTenant(TenantId tenantId);

    InventoryLocation saveLocation(InventoryLocation location);

    Optional<InventoryLocation> findLocationScoped(UUID locationId, TenantId tenantId, BranchId branchId);

    Optional<InventoryLocation> findLocationInTenant(UUID locationId, TenantId tenantId);

    List<InventoryLocation> findLocationsByBranch(TenantId tenantId, BranchId branchId);

    InventoryItem saveItem(InventoryItem item);

    Optional<InventoryItem> findItemScoped(UUID itemId, TenantId tenantId);

    List<InventoryItem> findItemsByTenant(TenantId tenantId);

    InventoryPolicy savePolicy(InventoryPolicy policy);

    Optional<InventoryPolicy> findPolicy(TenantId tenantId, BranchId branchId);

    InventoryBalance saveBalance(InventoryBalance balance);

    Optional<InventoryBalance> findBalanceScoped(TenantId tenantId, BranchId branchId, UUID stockLocationId, UUID inventoryItemId);

    List<InventoryBalance> findBalancesByBranch(TenantId tenantId, BranchId branchId);

    StockMovement appendMovement(StockMovement movement);

    Optional<StockMovement> findMovementByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey);

    List<StockMovement> findMovementsByBranch(TenantId tenantId, BranchId branchId, UUID inventoryItemId, int limit);

    Recipe saveRecipe(Recipe recipe);

    Optional<Recipe> findRecipeScoped(UUID recipeId, TenantId tenantId);

    Optional<Recipe> findActiveRecipeForProduct(TenantId tenantId, BranchId branchId, UUID productId, UUID productVariantId);

    List<Recipe> findRecipesByTenant(TenantId tenantId);

    StockReservation saveReservation(StockReservation reservation);

    Optional<StockReservation> findReservationByOrder(TenantId tenantId, BranchId branchId, UUID orderId);

    List<StockReservation> findActiveReservationsByBranch(TenantId tenantId, BranchId branchId);

    StockWasteRecord saveWasteRecord(StockWasteRecord wasteRecord);

    Optional<StockWasteRecord> findWasteByIdempotency(TenantId tenantId, BranchId branchId, String idempotencyKey);

    List<StockWasteRecord> findWasteByBranch(TenantId tenantId, BranchId branchId, int limit);

    StockCount saveStockCount(StockCount count);

    Optional<StockCount> findStockCountScoped(UUID countId, TenantId tenantId, BranchId branchId);

    List<StockCount> findStockCountsByBranch(TenantId tenantId, BranchId branchId);

    StockTransfer saveStockTransfer(StockTransfer transfer);

    Optional<StockTransfer> findStockTransferScoped(UUID transferId, TenantId tenantId);

    List<StockTransfer> findStockTransfersByTenant(TenantId tenantId);

    InventoryAlert saveAlert(InventoryAlert alert);

    Optional<InventoryAlert> findAlertScoped(UUID alertId, TenantId tenantId, BranchId branchId);

    Optional<InventoryAlert> findActiveAlertByDedup(TenantId tenantId, BranchId branchId, String dedupKey);

    List<InventoryAlert> findAlertsByBranch(TenantId tenantId, BranchId branchId, boolean activeOnly);

    // Outbox consumption & idempotency
    List<OutboxWorkItem> claimPendingInventoryOutboxEvents(int batchSize, Instant now);

    boolean isOutboxEventProcessed(UUID outboxEventId);

    void markOutboxEventProcessed(UUID outboxEventId, TenantId tenantId, BranchId branchId, String eventType, Instant processedAt);

    void markOutboxEventPublished(UUID outboxEventId, Instant publishedAt);

    void markOutboxEventRetry(UUID outboxEventId, int nextRetryCount, Instant nextAttemptAt, String status);
}
