package com.restaurantpos.domain.port;

public interface CashDrawerPort {
    record DrawerOpenResult(boolean openedOk, String deviceName, String hardwareStatus, String errorMessage) {
    }

    DrawerOpenResult openDrawer();
}
