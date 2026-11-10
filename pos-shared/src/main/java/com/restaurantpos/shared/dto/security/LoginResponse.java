package com.restaurantpos.shared.dto.security;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserProfileDto profile
) {
}
