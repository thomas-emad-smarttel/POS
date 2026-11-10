package com.restaurantpos.api.controller;

import com.restaurantpos.api.security.SecurityPrincipalResolver;
import com.restaurantpos.application.pos.TableManagementService;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.pos.DiningAreaDto;
import com.restaurantpos.shared.dto.pos.DiningTableDto;
import com.restaurantpos.shared.dto.pos.SaveDiningAreaRequest;
import com.restaurantpos.shared.dto.pos.SaveDiningTableRequest;
import com.restaurantpos.shared.dto.pos.UpdateTableStatusRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Branch dining areas and concurrency-safe table status endpoints.
 */
@RestController
@RequestMapping("/api/v1/tables")
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class TableController {

    private final TableManagementService tableService;

    public TableController(TableManagementService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/areas")
    public ApiResponse<List<DiningAreaDto>> listAreas() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(tableService.listAreas(actor), corr.value());
    }

    @PostMapping("/areas")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DiningAreaDto> createArea(@Valid @RequestBody SaveDiningAreaRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(tableService.createArea(actor, request, corr), corr.value());
    }

    @GetMapping
    public ApiResponse<List<DiningTableDto>> listTables() {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(tableService.listTables(actor), corr.value());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DiningTableDto> createTable(@Valid @RequestBody SaveDiningTableRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(tableService.createTable(actor, request, corr), corr.value());
    }

    @PatchMapping("/{tableId}/status")
    public ApiResponse<DiningTableDto> updateStatus(
            @PathVariable UUID tableId,
            @Valid @RequestBody UpdateTableStatusRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(tableService.updateTableStatus(actor, tableId, request, corr), corr.value());
    }
}
