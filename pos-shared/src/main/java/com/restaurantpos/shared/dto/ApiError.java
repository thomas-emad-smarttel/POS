package com.restaurantpos.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * Sanitized error envelope returned by backend APIs without leaking internal stack traces.
 */
public record ApiError(
        String code,
        String message,
        String correlationId,
        Instant timestamp,
        List<String> details
) {
    public static ApiError of(String code, String message, String correlationId) {
        return new ApiError(code, message, correlationId, Instant.now(), List.of());
    }

    public static ApiError of(String code, String message, String correlationId, List<String> details) {
        return new ApiError(code, message, correlationId, Instant.now(), details == null ? List.of() : List.copyOf(details));
    }
}
