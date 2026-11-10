package com.restaurantpos.api.controller;

import com.restaurantpos.api.filter.CorrelationIdFilter;
import com.restaurantpos.application.system.ServerHealthUseCase;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.ServerStatusDto;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes Local Server readiness and version metadata for POS terminals.
 */
@RestController
@RequestMapping("/api/v1/system")
public class HealthController {

    private final ServerHealthUseCase serverHealthUseCase;
    private final String serviceName;
    private final String version;
    private final String operatingMode;

    public HealthController(
            ServerHealthUseCase serverHealthUseCase,
            @Value("${pos.server.name:pos-local-server}") String serviceName,
            @Value("${pos.server.version:0.1.0-SNAPSHOT}") String version,
            @Value("${pos.server.mode:OFFLINE_CAPABLE}") String operatingMode
    ) {
        this.serverHealthUseCase = serverHealthUseCase;
        this.serviceName = serviceName;
        this.version = version;
        this.operatingMode = operatingMode;
    }

    @GetMapping("/health")
    public ApiResponse<ServerStatusDto> health() {
        ServerStatusDto status = serverHealthUseCase.checkHealth(serviceName, version, operatingMode);
        return ApiResponse.ok(status, MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
