package com.restaurantpos.api.controller;

import com.restaurantpos.api.security.SecurityPrincipalResolver;
import com.restaurantpos.application.pos.OrderManagementService;
import com.restaurantpos.domain.order.OrderStatus;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.pos.AddOrderItemRequest;
import com.restaurantpos.shared.dto.pos.ApplyDiscountRequest;
import com.restaurantpos.shared.dto.pos.CancelOrderRequest;
import com.restaurantpos.shared.dto.pos.ChangeOrderTypeRequest;
import com.restaurantpos.shared.dto.pos.CreateOrderRequest;
import com.restaurantpos.shared.dto.pos.MergeOrderRequest;
import com.restaurantpos.shared.dto.pos.OrderDto;
import com.restaurantpos.shared.dto.pos.OrderSummaryDto;
import com.restaurantpos.shared.dto.pos.SplitOrderRequest;
import com.restaurantpos.shared.dto.pos.TransferOrderRequest;
import com.restaurantpos.shared.dto.pos.UpdateOrderItemRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Branch-authoritative order creation, item modification, pricing, discount, transfer, split, merge, and kitchen dispatch endpoints.
 */
@RestController
@RequestMapping("/api/v1/orders")
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class OrderController {

    private final OrderManagementService orderService;

    public OrderController(OrderManagementService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDto> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.createOrder(actor, request, corr), corr.value());
    }

    @GetMapping
    public ApiResponse<List<OrderSummaryDto>> searchOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Boolean held,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.searchOrders(actor, status, held, page, Math.min(size, 100)), corr.value());
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto> getOrder(@PathVariable UUID orderId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.getOrder(actor, orderId), corr.value());
    }

    @PostMapping("/{orderId}/items")
    public ApiResponse<OrderDto> addItem(
            @PathVariable UUID orderId,
            @Valid @RequestBody AddOrderItemRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.addItem(actor, orderId, request, corr), corr.value());
    }

    @PutMapping("/{orderId}/items/{itemId}")
    public ApiResponse<OrderDto> updateItem(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateOrderItemRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.updateItem(actor, orderId, itemId, request, corr), corr.value());
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    public ApiResponse<OrderDto> removeItem(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @RequestParam long expectedVersion
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.removeItem(actor, orderId, itemId, expectedVersion, corr), corr.value());
    }

    @PostMapping("/{orderId}/discounts")
    public ApiResponse<OrderDto> applyDiscount(
            @PathVariable UUID orderId,
            @Valid @RequestBody ApplyDiscountRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.applyDiscount(actor, orderId, request, corr), corr.value());
    }

    @PostMapping("/{orderId}/hold")
    public ApiResponse<OrderDto> holdOrder(@PathVariable UUID orderId, @RequestParam long expectedVersion) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.holdOrder(actor, orderId, expectedVersion, corr), corr.value());
    }

    @PostMapping("/{orderId}/retrieve")
    public ApiResponse<OrderDto> retrieveOrder(@PathVariable UUID orderId, @RequestParam long expectedVersion) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.retrieveHeldOrder(actor, orderId, expectedVersion, corr), corr.value());
    }

    @PostMapping("/{orderId}/order-type")
    public ApiResponse<OrderDto> changeOrderType(
            @PathVariable UUID orderId,
            @Valid @RequestBody ChangeOrderTypeRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.changeOrderType(actor, orderId, request, corr), corr.value());
    }

    @PostMapping("/{orderId}/transfer")
    public ApiResponse<OrderDto> transferOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody TransferOrderRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.transferOrder(actor, orderId, request, corr), corr.value());
    }

    @PostMapping("/{orderId}/split")
    public ApiResponse<List<OrderDto>> splitOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody SplitOrderRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.splitOrder(actor, orderId, request, corr), corr.value());
    }

    @PostMapping("/{orderId}/merge")
    public ApiResponse<OrderDto> mergeOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody MergeOrderRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.mergeOrders(actor, orderId, request, corr), corr.value());
    }

    @PostMapping("/{orderId}/send-to-kitchen")
    public ApiResponse<OrderDto> sendToKitchen(@PathVariable UUID orderId, @RequestParam long expectedVersion) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.sendToKitchen(actor, orderId, expectedVersion, corr), corr.value());
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderDto> cancelOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody CancelOrderRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.cancelOrder(actor, orderId, request, corr), corr.value());
    }

    @PostMapping("/{orderId}/reopen")
    public ApiResponse<OrderDto> reopenOrder(@PathVariable UUID orderId, @RequestParam long expectedVersion) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(orderService.reopenOrder(actor, orderId, expectedVersion, corr), corr.value());
    }
}
