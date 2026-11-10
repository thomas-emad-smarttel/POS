package com.restaurantpos.infrastructure.time;

import com.restaurantpos.domain.port.SystemClockPort;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Production UTC clock implementation backed by {@link Instant#now()}.
 */
@Component
public class SystemClockAdapter implements SystemClockPort {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
