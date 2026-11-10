package com.restaurantpos.cloud.controller;

import com.restaurantpos.application.system.ServerHealthUseCase;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.ServerStatusDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Health endpoint for the optional Cloud synchronization backend.
 */
@RestController
@RequestMapping("/api/v1/cloud")
public class CloudHealthController {

    private final ServerHealthUseCase serverHealthUseCase;
    private final String version;

    public CloudHealthController(
            ServerHealthUseCase serverHealthUseCase,
            @Value("${pos.cloud.version:0.1.0-SNAPSHOT}") String version
    ) {
        this.serverHealthUseCase = serverHealthUseCase;
        this.version = version;
    }

    @GetMapping("/health")
    public ApiResponse<ServerStatusDto> health() {
        ServerStatusDto status = serverHealthUseCase.checkHealth("pos-cloud", version, "CLOUD_CENTRAL");
        return ApiResponse.ok(status, UUID.randomUUID().toString());
    }
}
