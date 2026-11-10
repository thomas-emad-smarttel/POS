package com.restaurantpos.desktop.ui.component;

import javafx.scene.control.Button;

/**
 * Reusable touch-friendly secondary button styled via centralized CSS Design Tokens.
 */
public final class SecondaryButton extends Button {

    public SecondaryButton(String text) {
        super(text);
        getStyleClass().add("pos-btn-secondary");
    }
}
