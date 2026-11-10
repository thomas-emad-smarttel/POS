package com.restaurantpos.devices.virtual;

import com.restaurantpos.devices.CashDrawer;
import com.restaurantpos.devices.DeviceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Virtual cash drawer adapter for automated testing and hardware-free environments.
 */
public class VirtualCashDrawer implements CashDrawer {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualCashDrawer.class);

    private final String name;
    private final AtomicInteger openCount = new AtomicInteger();
    private volatile boolean unavailable;
    private volatile String failureMessage;

    public VirtualCashDrawer(String name) {
        this.name = name;
    }

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    @Override
    public String deviceName() {
        return name;
    }

    @Override
    public DeviceStatus status() {
        if (failureMessage != null) {
            return DeviceStatus.ERROR;
        }
        return unavailable ? DeviceStatus.DISCONNECTED : DeviceStatus.SIMULATED;
    }

    @Override
    public void openDrawer() {
        if (failureMessage != null) {
            throw new IllegalStateException("Cash drawer hardware error: " + failureMessage);
        }
        if (unavailable) {
            throw new IllegalStateException("Cash drawer '" + name + "' is offline or unavailable");
        }
        int count = openCount.incrementAndGet();
        LOG.debug("Virtual cash drawer '{}' opened (count={})", name, count);
    }

    public int openCount() {
        return openCount.get();
    }
}
