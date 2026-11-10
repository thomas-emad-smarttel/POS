package com.restaurantpos.api.controller;

import com.restaurantpos.api.security.SecurityPrincipalResolver;
import com.restaurantpos.application.security.RbacManagementService;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.security.AssignUserBranchesRequest;
import com.restaurantpos.shared.dto.security.AssignUserRolesRequest;
import com.restaurantpos.shared.dto.security.BranchSummaryDto;
import com.restaurantpos.shared.dto.security.CreateUserRequest;
import com.restaurantpos.shared.dto.security.UpdateUserRequest;
import com.restaurantpos.shared.dto.security.UpdateUserStatusRequest;
import com.restaurantpos.shared.dto.security.UserSummaryDto;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
 * Tenant-isolated employee account, branch access, and role assignment endpoints.
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class UserManagementController {

    private final RbacManagementService managementService;

    public UserManagementController(RbacManagementService managementService) {
        this.managementService = managementService;
    }

    @GetMapping("/users")
    public ApiResponse<List<UserSummaryDto>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(managementService.listUsers(principal, page, Math.min(size, 100)), correlationId.value());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserSummaryDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(managementService.createUser(principal, request, correlationId), correlationId.value());
    }

    @PatchMapping("/users/{userId}")
    public ApiResponse<UserSummaryDto> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(managementService.updateUser(principal, userId, request, correlationId), correlationId.value());
    }

    @PostMapping("/users/{userId}/status")
    public ApiResponse<UserSummaryDto> setUserStatus(
            @PathVariable UUID userId,
            @RequestBody UpdateUserStatusRequest request
    ) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(managementService.setUserActive(principal, userId, request.active(), correlationId), correlationId.value());
    }

    @PutMapping("/users/{userId}/branches")
    public ApiResponse<UserSummaryDto> assignBranches(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignUserBranchesRequest request
    ) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(managementService.assignBranches(principal, userId, request, correlationId), correlationId.value());
    }

    @PutMapping("/users/{userId}/roles")
    public ApiResponse<UserSummaryDto> assignRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignUserRolesRequest request
    ) {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(managementService.assignUserRoles(principal, userId, request, correlationId), correlationId.value());
    }

    @GetMapping("/branches")
    public ApiResponse<List<BranchSummaryDto>> listBranches() {
        var principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(managementService.listBranches(principal), correlationId.value());
    }
}
