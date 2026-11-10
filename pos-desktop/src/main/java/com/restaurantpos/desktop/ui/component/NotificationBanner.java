package com.restaurantpos.desktop.ui.component;

import javafx.scene.control.Label;

/**
 * Inline dismissible feedback banner for success and validation/API errors.
 */
public final class NotificationBanner extends Label {

    public NotificationBanner() {
        setWrapText(true);
        setMaxWidth(Double.MAX_VALUE);
        clear();
    }

    public void showSuccess(String message) {
        getStyleClass().removeAll("pos-banner-error", "pos-banner-success");
        getStyleClass().add("pos-banner-success");
        setText(message);
        setVisible(true);
        setManaged(true);
    }

    public void showError(String message) {
        getStyleClass().removeAll("pos-banner-error", "pos-banner-success");
        getStyleClass().add("pos-banner-error");
        setText(message);
        setVisible(true);
        setManaged(true);
    }

    public void clear() {
        setText("");
        setVisible(false);
        setManaged(false);
    }
}
