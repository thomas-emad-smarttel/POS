package com.restaurantpos.shared.dto;

import java.time.Instant;

/**
 * Standard API response envelope across Local Server and Cloud Server endpoints.
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        String correlationId,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data, String correlationId) {
        return new ApiResponse<>(true, data, null, correlationId, Instant.now());
    }

    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error, error.correlationId(), Instant.now());
    }
}
