package com.restaurantpos.application.system;

import com.restaurantpos.domain.port.SystemClockPort;
import com.restaurantpos.shared.dto.ServerStatusDto;

import java.util.Objects;

/**
 * Reports operational health and operating mode for server components.
 */
public class ServerHealthUseCase {

    private final SystemClockPort clockPort;

    public ServerHealthUseCase(SystemClockPort clockPort) {
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort must not be null");
    }

    public ServerStatusDto checkHealth(String serviceName, String version, String operatingMode) {
        return new ServerStatusDto(
                Objects.requireNonNull(serviceName, "serviceName must not be null"),
                "UP",
                Objects.requireNonNull(version, "version must not be null"),
                Objects.requireNonNull(operatingMode, "operatingMode must not be null"),
                clockPort.now()
        );
    }
}
