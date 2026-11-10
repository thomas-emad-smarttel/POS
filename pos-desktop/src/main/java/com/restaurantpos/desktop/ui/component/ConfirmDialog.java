package com.restaurantpos.desktop.ui.component;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/**
 * Reusable confirmation prompt for sensitive RBAC, user deactivation, and session revocation actions.
 */
public final class ConfirmDialog {

    private ConfirmDialog() {
    }

    public static boolean confirm(String title, String header, String body) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(body);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}
