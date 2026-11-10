package com.restaurantpos.desktop.ui.component;

import javafx.scene.control.Button;

/**
 * Reusable touch-friendly primary CTA button styled via centralized CSS Design Tokens.
 */
public final class PrimaryButton extends Button {

    public PrimaryButton(String text) {
        super(text);
        getStyleClass().add("pos-btn-primary");
    }
}
