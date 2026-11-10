package com.restaurantpos.api.controller;

import com.restaurantpos.api.security.SecurityPrincipalResolver;
import com.restaurantpos.application.inventory.InventoryCatalogService;
import com.restaurantpos.application.inventory.InventoryOutboxConsumerService;
import com.restaurantpos.application.inventory.RecipeManagementService;
import com.restaurantpos.application.inventory.StockCountService;
import com.restaurantpos.application.inventory.StockLedgerService;
import com.restaurantpos.application.inventory.StockTransferService;
import com.restaurantpos.application.security.PermissionPolicy;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.inventory.CreateStockCountRequest;
import com.restaurantpos.shared.dto.inventory.CreateStockTransferRequest;
import com.restaurantpos.shared.dto.inventory.InventoryAlertDto;
import com.restaurantpos.shared.dto.inventory.InventoryBalanceDto;
import com.restaurantpos.shared.dto.inventory.InventoryCategoryDto;
import com.restaurantpos.shared.dto.inventory.InventoryItemDto;
import com.restaurantpos.shared.dto.inventory.InventoryLocationDto;
import com.restaurantpos.shared.dto.inventory.InventoryPolicyDto;
import com.restaurantpos.shared.dto.inventory.InventoryValuationReportDto;
import com.restaurantpos.shared.dto.inventory.ReceiveStockTransferRequest;
import com.restaurantpos.shared.dto.inventory.RecipeCostPreviewDto;
import com.restaurantpos.shared.dto.inventory.RecipeDto;
import com.restaurantpos.shared.dto.inventory.RecordStockAdjustmentRequest;
import com.restaurantpos.shared.dto.inventory.RecordWasteRequest;
import com.restaurantpos.shared.dto.inventory.ResolveInventoryAlertRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryCategoryRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryItemRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryLocationRequest;
import com.restaurantpos.shared.dto.inventory.SaveRecipeRequest;
import com.restaurantpos.shared.dto.inventory.SaveUnitConversionRequest;
import com.restaurantpos.shared.dto.inventory.SaveUnitRequest;
import com.restaurantpos.shared.dto.inventory.StockCountActionRequest;
import com.restaurantpos.shared.dto.inventory.StockCountDto;
import com.restaurantpos.shared.dto.inventory.StockMovementDto;
import com.restaurantpos.shared.dto.inventory.StockReservationDto;
import com.restaurantpos.shared.dto.inventory.StockTransferActionRequest;
import com.restaurantpos.shared.dto.inventory.StockTransferDto;
import com.restaurantpos.shared.dto.inventory.StockWasteRecordDto;
import com.restaurantpos.shared.dto.inventory.UnitConversionDto;
import com.restaurantpos.shared.dto.inventory.UnitOfMeasureDto;
import com.restaurantpos.shared.dto.inventory.UpdateInventoryPolicyRequest;
import com.restaurantpos.shared.dto.inventory.UpdateStockCountItemsRequest;
import com.restaurantpos.shared.security.PermissionCode;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API surface for Phase 7 Inventory Management, Recipes, Consumption Outbox,
 * Waste, Physical Stock Counts, Inter-Branch Transfers, Alerts & Valuation Reports.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryController {

    private final InventoryCatalogService catalogService;
    private final RecipeManagementService recipeService;
    private final StockLedgerService ledgerService;
    private final StockCountService countService;
    private final StockTransferService transferService;
    private final InventoryOutboxConsumerService outboxConsumerService;

    public InventoryController(
            InventoryCatalogService catalogService,
            RecipeManagementService recipeService,
            StockLedgerService ledgerService,
            StockCountService countService,
            StockTransferService transferService,
            InventoryOutboxConsumerService outboxConsumerService
    ) {
        this.catalogService = catalogService;
        this.recipeService = recipeService;
        this.ledgerService = ledgerService;
        this.countService = countService;
        this.transferService = transferService;
        this.outboxConsumerService = outboxConsumerService;
    }

    @GetMapping("/units")
    public ApiResponse<List<UnitOfMeasureDto>> listUnits() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.listUnits(actor), corr.value());
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UnitOfMeasureDto> createUnit(@Valid @RequestBody SaveUnitRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.createUnit(actor, request, corr), corr.value());
    }

    @GetMapping("/unit-conversions")
    public ApiResponse<List<UnitConversionDto>> listUnitConversions() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.listUnitConversions(actor), corr.value());
    }

    @PostMapping("/unit-conversions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UnitConversionDto> saveUnitConversion(@Valid @RequestBody SaveUnitConversionRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.saveUnitConversion(actor, request, corr), corr.value());
    }

    @GetMapping("/categories")
    public ApiResponse<List<InventoryCategoryDto>> listCategories() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.listCategories(actor), corr.value());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryCategoryDto> createCategory(@Valid @RequestBody SaveInventoryCategoryRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.createCategory(actor, request, corr), corr.value());
    }

    @GetMapping("/locations")
    public ApiResponse<List<InventoryLocationDto>> listLocations() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.listLocations(actor), corr.value());
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryLocationDto> createLocation(@Valid @RequestBody SaveInventoryLocationRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.createLocation(actor, request, corr), corr.value());
    }

    @GetMapping("/items")
    public ApiResponse<List<InventoryItemDto>> listItems() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.listItems(actor), corr.value());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryItemDto> createItem(@Valid @RequestBody SaveInventoryItemRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.createItem(actor, request, corr), corr.value());
    }

    @PutMapping("/items/{itemId}")
    public ApiResponse<InventoryItemDto> updateItem(
            @PathVariable UUID itemId,
            @Valid @RequestBody SaveInventoryItemRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.updateItem(actor, itemId, request, corr), corr.value());
    }

    @GetMapping("/policy")
    public ApiResponse<InventoryPolicyDto> getPolicy() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.getPolicy(actor), corr.value());
    }

    @PutMapping("/policy")
    public ApiResponse<InventoryPolicyDto> updatePolicy(@Valid @RequestBody UpdateInventoryPolicyRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.updatePolicy(actor, request, corr), corr.value());
    }

    @GetMapping("/balances")
    public ApiResponse<List<InventoryBalanceDto>> listBalances() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(ledgerService.listBalances(actor), corr.value());
    }

    @GetMapping("/movements")
    public ApiResponse<List<StockMovementDto>> listMovements(
            @RequestParam(required = false) UUID itemId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(ledgerService.listMovements(actor, itemId, limit), corr.value());
    }

    @PostMapping("/adjustments")
    public ApiResponse<StockMovementDto> recordAdjustment(@Valid @RequestBody RecordStockAdjustmentRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(ledgerService.recordAdjustment(actor, request, corr), corr.value());
    }

    @GetMapping("/waste")
    public ApiResponse<List<StockWasteRecordDto>> listWaste(@RequestParam(defaultValue = "100") int limit) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(ledgerService.listWasteRecords(actor, limit), corr.value());
    }

    @PostMapping("/waste")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StockWasteRecordDto> recordWaste(@Valid @RequestBody RecordWasteRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(ledgerService.recordWaste(actor, request, corr), corr.value());
    }

    @GetMapping("/reservations")
    public ApiResponse<List<StockReservationDto>> listReservations() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(ledgerService.listReservations(actor), corr.value());
    }

    @PostMapping("/outbox/process")
    public ApiResponse<Integer> processInventoryOutbox(@RequestParam(defaultValue = "25") int batchSize) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        PermissionCode perm = actor.hasPermission(PermissionCode.INVENTORY_ITEM_MANAGE)
                ? PermissionCode.INVENTORY_ITEM_MANAGE : PermissionCode.INVENTORY_MANAGE;
        PermissionPolicy.requireBranchPermission(actor, actor.tenantId(), actor.activeBranchId(), perm);
        return ApiResponse.ok(outboxConsumerService.processPendingEvents(batchSize), corr.value());
    }

    @GetMapping("/recipes")
    public ApiResponse<List<RecipeDto>> listRecipes() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(recipeService.listRecipes(actor), corr.value());
    }

    @GetMapping("/recipes/{recipeId}")
    public ApiResponse<RecipeDto> getRecipe(@PathVariable UUID recipeId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(recipeService.getRecipe(actor, recipeId), corr.value());
    }

    @PostMapping("/recipes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RecipeDto> createRecipe(@Valid @RequestBody SaveRecipeRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(recipeService.createOrNewVersion(actor, null, request, corr), corr.value());
    }

    @PostMapping("/recipes/{recipeId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RecipeDto> addRecipeVersion(
            @PathVariable UUID recipeId,
            @Valid @RequestBody SaveRecipeRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(recipeService.createOrNewVersion(actor, recipeId, request, corr), corr.value());
    }

    @PostMapping("/recipes/{recipeId}/versions/{versionId}/publish")
    public ApiResponse<RecipeDto> publishRecipeVersion(
            @PathVariable UUID recipeId,
            @PathVariable UUID versionId
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(recipeService.publishVersion(actor, recipeId, versionId, corr), corr.value());
    }

    @GetMapping("/recipes/{recipeId}/cost")
    public ApiResponse<RecipeCostPreviewDto> previewRecipeCost(
            @PathVariable UUID recipeId,
            @RequestParam(required = false) UUID versionId
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(recipeService.previewRecipeCost(actor, recipeId, versionId), corr.value());
    }

    @GetMapping("/counts")
    public ApiResponse<List<StockCountDto>> listCounts() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(countService.listCounts(actor), corr.value());
    }

    @PostMapping("/counts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StockCountDto> createCount(@Valid @RequestBody CreateStockCountRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(countService.createCount(actor, request, corr), corr.value());
    }

    @PutMapping("/counts/{countId}/items")
    public ApiResponse<StockCountDto> updateCountItems(
            @PathVariable UUID countId,
            @Valid @RequestBody UpdateStockCountItemsRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(countService.updateCountedItems(actor, countId, request), corr.value());
    }

    @PostMapping("/counts/{countId}/submit")
    public ApiResponse<StockCountDto> submitCount(
            @PathVariable UUID countId,
            @Valid @RequestBody StockCountActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(countService.submitCount(actor, countId, request, corr), corr.value());
    }

    @PostMapping("/counts/{countId}/recount")
    public ApiResponse<StockCountDto> requestRecount(
            @PathVariable UUID countId,
            @Valid @RequestBody StockCountActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(countService.requestRecount(actor, countId, request, corr), corr.value());
    }

    @PostMapping("/counts/{countId}/approve")
    public ApiResponse<StockCountDto> approveCount(
            @PathVariable UUID countId,
            @Valid @RequestBody StockCountActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(countService.approveCount(actor, countId, request, corr), corr.value());
    }

    @PostMapping("/counts/{countId}/post")
    public ApiResponse<StockCountDto> postCount(
            @PathVariable UUID countId,
            @Valid @RequestBody StockCountActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(countService.postCount(actor, countId, request, corr), corr.value());
    }

    @GetMapping("/transfers")
    public ApiResponse<List<StockTransferDto>> listTransfers() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(transferService.listTransfers(actor), corr.value());
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StockTransferDto> createTransfer(@Valid @RequestBody CreateStockTransferRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(transferService.createTransfer(actor, request, corr), corr.value());
    }

    @PostMapping("/transfers/{transferId}/approve")
    public ApiResponse<StockTransferDto> approveTransfer(
            @PathVariable UUID transferId,
            @Valid @RequestBody StockTransferActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(transferService.approveTransfer(actor, transferId, request, corr), corr.value());
    }

    @PostMapping("/transfers/{transferId}/dispatch")
    public ApiResponse<StockTransferDto> dispatchTransfer(
            @PathVariable UUID transferId,
            @Valid @RequestBody StockTransferActionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(transferService.dispatchTransfer(actor, transferId, request, corr), corr.value());
    }

    @PostMapping("/transfers/{transferId}/receive")
    public ApiResponse<StockTransferDto> receiveTransfer(
            @PathVariable UUID transferId,
            @Valid @RequestBody ReceiveStockTransferRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(transferService.receiveTransfer(actor, transferId, request, corr), corr.value());
    }

    @GetMapping("/alerts")
    public ApiResponse<List<InventoryAlertDto>> listAlerts(@RequestParam(defaultValue = "true") boolean activeOnly) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.listAlerts(actor, activeOnly), corr.value());
    }

    @PostMapping("/alerts/{alertId}/resolve")
    public ApiResponse<InventoryAlertDto> resolveAlert(
            @PathVariable UUID alertId,
            @Valid @RequestBody ResolveInventoryAlertRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(catalogService.resolveAlert(actor, alertId, request, corr), corr.value());
    }

    @GetMapping("/reports/low-stock")
    public ApiResponse<InventoryValuationReportDto> lowStockReport() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(ledgerService.buildValuationReport(actor, true), corr.value());
    }

    @GetMapping("/reports/valuation")
    public ApiResponse<InventoryValuationReportDto> valuationReport() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(ledgerService.buildValuationReport(actor, false), corr.value());
    }
}
