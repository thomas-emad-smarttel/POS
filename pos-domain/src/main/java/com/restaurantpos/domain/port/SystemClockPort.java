package com.restaurantpos.domain.port;

import java.time.Instant;

/**
 * Clock abstraction enabling deterministic unit tests across domain and application layers.
 */
public interface SystemClockPort {
    Instant now();
}
