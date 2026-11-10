package com.restaurantpos.api.controller;

import com.restaurantpos.api.security.SecurityPrincipalResolver;
import com.restaurantpos.application.security.AuthenticationService;
import com.restaurantpos.domain.security.AccessPrincipal;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.security.ChangePasswordRequest;
import com.restaurantpos.shared.dto.security.LoginRequest;
import com.restaurantpos.shared.dto.security.LoginResponse;
import com.restaurantpos.shared.dto.security.SwitchBranchRequest;
import com.restaurantpos.shared.dto.security.UserProfileDto;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication and active session endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(authenticationService.login(request, correlationId), correlationId.value());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        AccessPrincipal principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        authenticationService.logout(principal, correlationId);
        return ApiResponse.ok(null, correlationId.value());
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileDto> me() {
        AccessPrincipal principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(authenticationService.getCurrentProfile(principal), correlationId.value());
    }

    @PostMapping("/switch-branch")
    public ApiResponse<UserProfileDto> switchBranch(@Valid @RequestBody SwitchBranchRequest request) {
        AccessPrincipal principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        return ApiResponse.ok(
                authenticationService.switchBranch(principal, request.branchId(), correlationId),
                correlationId.value()
        );
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        AccessPrincipal principal = SecurityPrincipalResolver.currentPrincipal();
        var correlationId = SecurityPrincipalResolver.currentCorrelationId();
        authenticationService.changePassword(principal, request, correlationId);
        return ApiResponse.ok(null, correlationId.value());
    }
}
