package com.restaurantpos.shared.dto;

import java.time.Instant;

/**
 * Health and readiness summary exposed by Local Server and Cloud Server.
 */
public record ServerStatusDto(
        String serviceName,
        String status,
        String version,
        String operatingMode,
        Instant serverTime
) {
}
