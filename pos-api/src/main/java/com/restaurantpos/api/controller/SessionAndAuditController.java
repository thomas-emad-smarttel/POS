package com.restaurantpos.api.controller;

import com.restaurantpos.api.security.SecurityPrincipalResolver;
import com.restaurantpos.application.security.RbacManagementService;
import com.restaurantpos.application.security.SupervisorApprovalService;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.security.AuditLogDto;
import com.restaurantpos.shared.dto.security.SessionDto;
import com.restaurantpos.shared.dto.security.SupervisorApprovalRequest;
import com.restaurantpos.shared.dto.security.SupervisorApprovalResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Active session governance, audit log inspection, and supervisor approval endpoints.
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class SessionAndAuditController {

    private final RbacManagementService managementService;
    private final SupervisorApprovalService approvalService;

    public SessionAndAuditController(
            RbacManagementService managementService,
            SupervisorApprovalService approvalService
    ) {
        this.managementService = managementService;
        this.approvalService = approvalService;
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionDto>> listSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(managementService.listSessions(principal, page, Math.min(size, 100)), correlationId.value());
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    public ApiResponse<Void> revokeSession(@PathVariable UUID sessionId) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        managementService.revokeSession(principal, sessionId, correlationId);
        return ApiResponse.ok(null, correlationId.value());
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AuditLogDto>> listAuditLogs(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(
                managementService.listAuditLogs(principal, branchId, page, Math.min(size, 100)),
                correlationId.value()
        );
    }

    @PostMapping("/approvals/authorize")
    public ApiResponse<SupervisorApprovalResponse> authorizeSensitiveAction(
            @Valid @RequestBody SupervisorApprovalRequest request
    ) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(approvalService.authorize(principal, request, correlationId), correlationId.value());
    }
}
