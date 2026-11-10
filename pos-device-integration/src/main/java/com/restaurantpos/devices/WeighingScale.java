package com.restaurantpos.devices;

import java.math.BigDecimal;

/**
 * Hardware abstraction interface for RS232/USB digital weighing scales.
 */
public interface WeighingScale {
    String deviceName();

    DeviceStatus status();

    BigDecimal readWeightKilograms();
}
