package com.restaurantpos.desktop;

import javafx.application.Application;

/**
 * Non-Application launcher wrapper required for reliable cross-platform classpath execution.
 */
public final class PosDesktopLauncher {

    private PosDesktopLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(PosDesktopApplication.class, args);
    }
}
