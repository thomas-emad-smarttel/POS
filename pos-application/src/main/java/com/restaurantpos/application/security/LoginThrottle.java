package com.restaurantpos.application.security;

import com.restaurantpos.domain.exception.AuthenticationDomainException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window login throttle preventing rapid brute-force bursts before password hashing.
 */
public class LoginThrottle {

    private record AttemptWindow(Instant windowStart, int count) {
    }

    private final long windowSeconds;
    private final int maxAttempts;
    private final Map<String, AttemptWindow> windows = new ConcurrentHashMap<>();

    public LoginThrottle(long windowSeconds, int maxAttempts) {
        this.windowSeconds = windowSeconds;
        this.maxAttempts = maxAttempts;
    }

    public void checkAndRecordAttempt(String key, Instant now) {
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().windowStart().plusSeconds(windowSeconds).isBefore(now));
        }
        AttemptWindow updated = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart().plusSeconds(windowSeconds).isBefore(now)) {
                return new AttemptWindow(now, 1);
            }
            return new AttemptWindow(existing.windowStart(), existing.count() + 1);
        });
        if (updated.count() > maxAttempts) {
            throw new AuthenticationDomainException(
                    "AUTH_THROTTLED",
                    "Too many login attempts. Please wait and try again."
            );
        }
    }

    public void reset(String key) {
        windows.remove(key);
    }
}
