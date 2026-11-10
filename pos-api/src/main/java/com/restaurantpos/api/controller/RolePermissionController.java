package com.restaurantpos.api.controller;

import com.restaurantpos.api.security.SecurityPrincipalResolver;
import com.restaurantpos.application.security.RbacManagementService;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.security.CreateRoleRequest;
import com.restaurantpos.shared.dto.security.PermissionDto;
import com.restaurantpos.shared.dto.security.RoleDto;
import com.restaurantpos.shared.dto.security.UpdateRolePermissionsRequest;
import com.restaurantpos.shared.dto.security.UpdateRoleRequest;
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
 * RBAC role management, permission catalog, and role-permission matrix endpoints.
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class RolePermissionController {

    private final RbacManagementService managementService;

    public RolePermissionController(RbacManagementService managementService) {
        this.managementService = managementService;
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleDto>> listRoles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(managementService.listRoles(principal, page, Math.min(size, 100)), correlationId.value());
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoleDto> createRole(@Valid @RequestBody CreateRoleRequest request) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(managementService.createRole(principal, request, correlationId), correlationId.value());
    }

    @PutMapping("/roles/{roleId}")
    public ApiResponse<RoleDto> updateRole(
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(managementService.updateRole(principal, roleId, request, correlationId), correlationId.value());
    }

    @PutMapping("/roles/{roleId}/permissions")
    public ApiResponse<RoleDto> updateRolePermissions(
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRolePermissionsRequest request
    ) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(
                managementService.updateRolePermissions(principal, roleId, request, correlationId),
                correlationId.value()
        );
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionDto>> listPermissions() {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(managementService.listPermissions(principal), correlationId.value());
    }
}
