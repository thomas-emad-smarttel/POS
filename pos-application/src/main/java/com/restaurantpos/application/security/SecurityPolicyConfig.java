package com.restaurantpos.application.security;

/**
 * Configurable authentication, lockout, session lifecycle, and approval policies.
 */
public record SecurityPolicyConfig(
        long sessionTtlMinutes,
        int maxActiveSessionsPerUser,
        int maxFailedLoginAttempts,
        long lockoutDurationMinutes,
        long throttleWindowSeconds,
        int maxThrottleAttemptsPerKey,
        long approvalTtlMinutes
) {
    public static SecurityPolicyConfig defaults() {
        return new SecurityPolicyConfig(
                480L,
                5,
                5,
                15L,
                60L,
                10,
                5L
        );
    }
}
