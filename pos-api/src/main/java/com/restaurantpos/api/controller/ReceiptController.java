package com.restaurantpos.api.controller;

import com.restaurantpos.api.security.SecurityPrincipalResolver;
import com.restaurantpos.application.payment.ReceiptService;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.payment.GenerateReceiptRequest;
import com.restaurantpos.shared.dto.payment.ReceiptDto;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for receipt generation, ESC/POS printing, and duplicate reprinting.
 */
@RestController
@RequestMapping("/api/v1/receipts")
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<List<ReceiptDto>> listReceiptsForOrder(@PathVariable UUID orderId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(receiptService.listForOrder(actor, orderId), corr.value());
    }

    @GetMapping("/{receiptId}")
    public ApiResponse<ReceiptDto> getReceipt(@PathVariable UUID receiptId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(receiptService.getReceipt(actor, receiptId), corr.value());
    }

    @PostMapping("/orders/{orderId}/generate")
    public ApiResponse<ReceiptDto> generateForOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) GenerateReceiptRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(
                receiptService.generateForOrder(actor, orderId, null, null, "CUSTOMER", request, corr),
                corr.value()
        );
    }

    @PostMapping("/{receiptId}/reprint")
    public ApiResponse<ReceiptDto> reprintReceipt(@PathVariable UUID receiptId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(receiptService.reprint(actor, receiptId, corr), corr.value());
    }
}
