package com.restaurantpos.devices.virtual;

import com.restaurantpos.devices.DeviceStatus;
import com.restaurantpos.devices.ReceiptPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * In-memory virtual receipt printer for hardware-free testing and local development.
 */
public class VirtualReceiptPrinter implements ReceiptPrinter {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualReceiptPrinter.class);

    private final String name;
    private final List<byte[]> printedPayloads = Collections.synchronizedList(new ArrayList<>());
    private volatile int cutCount;
    private volatile boolean unavailable;
    private volatile String failureMessage;

    public VirtualReceiptPrinter(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
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
    public void printReceipt(byte[] escPosPayload) {
        Objects.requireNonNull(escPosPayload, "escPosPayload must not be null");
        if (failureMessage != null) {
            throw new IllegalStateException("Receipt printer error: " + failureMessage);
        }
        if (unavailable) {
            throw new IllegalStateException("Receipt printer '" + name + "' is unavailable");
        }
        printedPayloads.add(escPosPayload.clone());
        LOG.debug("Virtual printer '{}' captured {} bytes", name, escPosPayload.length);
    }

    @Override
    public void cutPaper() {
        if (failureMessage != null || unavailable) {
            return;
        }
        cutCount++;
        LOG.debug("Virtual printer '{}' cut paper (total cuts: {})", name, cutCount);
    }

    public List<byte[]> capturedPayloads() {
        synchronized (printedPayloads) {
            return List.copyOf(printedPayloads);
        }
    }

    public int cutCount() {
        return cutCount;
    }
}
