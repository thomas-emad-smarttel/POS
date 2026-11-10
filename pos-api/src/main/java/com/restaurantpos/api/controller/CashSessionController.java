package com.restaurantpos.api.controller;

import com.restaurantpos.api.security.SecurityPrincipalResolver;
import com.restaurantpos.application.payment.CashSessionService;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.payment.CashSessionDto;
import com.restaurantpos.shared.dto.payment.CloseCashSessionRequest;
import com.restaurantpos.shared.dto.payment.DrawerEventDto;
import com.restaurantpos.shared.dto.payment.OpenCashSessionRequest;
import com.restaurantpos.shared.dto.payment.OpenDrawerRequest;
import com.restaurantpos.shared.dto.payment.RecordCashMovementRequest;
import com.restaurantpos.shared.dto.payment.ReopenCashSessionRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for cashier cash sessions, append-only cash movements, closing reconciliation,
 * authorized reopening, and RBAC-enforced cash drawer opening.
 */
@RestController
@RequestMapping("/api/v1/cash-sessions")
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class CashSessionController {

    private final CashSessionService cashSessionService;

    public CashSessionController(CashSessionService cashSessionService) {
        this.cashSessionService = cashSessionService;
    }

    @GetMapping
    public ApiResponse<List<CashSessionDto>> listBranchSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(cashSessionService.listBranchSessions(actor, page, size), corr.value());
    }

    @GetMapping("/active")
    public ApiResponse<CashSessionDto> getActiveSession(@RequestParam(required = false) UUID terminalId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(cashSessionService.findActiveSession(actor, terminalId).orElse(null), corr.value());
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<CashSessionDto> getSession(@PathVariable UUID sessionId) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(cashSessionService.getSessionDetail(actor, sessionId), corr.value());
    }

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CashSessionDto> openSession(@Valid @RequestBody OpenCashSessionRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(cashSessionService.openSession(actor, request, corr), corr.value());
    }

    @PostMapping("/{sessionId}/movements")
    public ApiResponse<CashSessionDto> recordMovement(
            @PathVariable UUID sessionId,
            @Valid @RequestBody RecordCashMovementRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(cashSessionService.recordMovement(actor, sessionId, request, corr), corr.value());
    }

    @PostMapping("/{sessionId}/close")
    public ApiResponse<CashSessionDto> closeSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CloseCashSessionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(cashSessionService.closeSession(actor, sessionId, request, corr), corr.value());
    }

    @PostMapping("/{sessionId}/reconcile")
    public ApiResponse<CashSessionDto> reconcileSession(
            @PathVariable UUID sessionId,
            @RequestParam long expectedVersion
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(cashSessionService.reconcileSession(actor, sessionId, expectedVersion, corr), corr.value());
    }

    @PostMapping("/{sessionId}/reopen")
    public ApiResponse<CashSessionDto> reopenSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ReopenCashSessionRequest request
    ) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(cashSessionService.reopenSession(actor, sessionId, request, corr), corr.value());
    }

    @PostMapping("/drawer/open")
    public ApiResponse<DrawerEventDto> openDrawerManually(@Valid @RequestBody OpenDrawerRequest request) {
        var actor = SecurityPrincipalResolver.currentPrincipal();
        var corr = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(cashSessionService.openDrawerManually(actor, request, corr), corr.value());
    }
}
