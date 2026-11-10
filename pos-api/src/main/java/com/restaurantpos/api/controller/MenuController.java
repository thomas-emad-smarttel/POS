package com.restaurantpos.api.controller;

import com.restaurantpos.api.security.SecurityPrincipalResolver;
import com.restaurantpos.application.pos.MenuManagementService;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.pos.BranchCategoryVisibilityRequest;
import com.restaurantpos.shared.dto.pos.BranchProductOverrideRequest;
import com.restaurantpos.shared.dto.pos.CategoryDto;
import com.restaurantpos.shared.dto.pos.CreateTaxRequest;
import com.restaurantpos.shared.dto.pos.ModifierDto;
import com.restaurantpos.shared.dto.pos.ModifierGroupDto;
import com.restaurantpos.shared.dto.pos.ProductDto;
import com.restaurantpos.shared.dto.pos.ProductVariantDto;
import com.restaurantpos.shared.dto.pos.SaveCategoryRequest;
import com.restaurantpos.shared.dto.pos.SaveModifierGroupRequest;
import com.restaurantpos.shared.dto.pos.SaveModifierRequest;
import com.restaurantpos.shared.dto.pos.SaveProductRequest;
import com.restaurantpos.shared.dto.pos.SaveProductVariantRequest;
import com.restaurantpos.shared.dto.pos.TaxDto;
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
 * Branch-aware menu, category, product, variant, modifier, and override REST endpoints.
 */
@RestController
@RequestMapping("/api/v1/menu")
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class MenuController {

    private final MenuManagementService menuService;

    public MenuController(MenuManagementService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/taxes")
    public ApiResponse<List<TaxDto>> listTaxes() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(menuService.listTaxes(actor), corr.value());
    }

    @PostMapping("/taxes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaxDto> createTax(@Valid @RequestBody CreateTaxRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(menuService.createTax(actor, request, corr), corr.value());
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryDto>> listCategories() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(menuService.listBranchCategories(actor), corr.value());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryDto> createCategory(@Valid @RequestBody SaveCategoryRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(menuService.createCategory(actor, request, corr), corr.value());
    }

    @PutMapping("/categories/{categoryId}")
    public ApiResponse<CategoryDto> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody SaveCategoryRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(menuService.updateCategory(actor, categoryId, request, corr), corr.value());
    }

    @PutMapping("/categories/{categoryId}/branch-visibility")
    public ApiResponse<Void> setBranchCategoryVisibility(
            @PathVariable UUID categoryId,
            @Valid @RequestBody BranchCategoryVisibilityRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        menuService.setBranchCategoryVisibility(actor, categoryId, request, corr);
        return ApiResponse.ok(null, corr.value());
    }

    @GetMapping("/modifier-groups")
    public ApiResponse<List<ModifierGroupDto>> listModifierGroups() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(menuService.listModifierGroups(actor), corr.value());
    }

    @PostMapping("/modifier-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ModifierGroupDto> createModifierGroup(@Valid @RequestBody SaveModifierGroupRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(menuService.createModifierGroup(actor, request, corr), corr.value());
    }

    @PostMapping("/modifier-groups/{groupId}/modifiers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ModifierDto> createModifier(
            @PathVariable UUID groupId,
            @Valid @RequestBody SaveModifierRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(menuService.createModifier(actor, groupId, request, corr), corr.value());
    }

    @GetMapping("/products")
    public ApiResponse<List<ProductDto>> searchProducts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(
                menuService.searchBranchProducts(actor, categoryId, query, page, Math.min(size, 100)),
                corr.value()
        );
    }

    @GetMapping("/products/{productId}")
    public ApiResponse<ProductDto> getProduct(@PathVariable UUID productId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(menuService.getProductDetail(actor, productId), corr.value());
    }

    @GetMapping("/products/by-barcode/{barcode}")
    public ApiResponse<ProductDto> getProductByBarcode(@PathVariable String barcode) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(menuService.findByBarcode(actor, barcode), corr.value());
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductDto> createProduct(@Valid @RequestBody SaveProductRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(menuService.createProduct(actor, request, corr), corr.value());
    }

    @PutMapping("/products/{productId}")
    public ApiResponse<ProductDto> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody SaveProductRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(menuService.updateProduct(actor, productId, request, corr), corr.value());
    }

    @PostMapping("/products/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductVariantDto> createVariant(
            @PathVariable UUID productId,
            @Valid @RequestBody SaveProductVariantRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(menuService.createVariant(actor, productId, request, corr), corr.value());
    }

    @PutMapping("/products/{productId}/branch-override")
    public ApiResponse<Void> setBranchProductOverride(
            @PathVariable UUID productId,
            @Valid @RequestBody BranchProductOverrideRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        menuService.setBranchProductOverride(actor, productId, request, corr);
        return ApiResponse.ok(null, corr.value());
    }
}
