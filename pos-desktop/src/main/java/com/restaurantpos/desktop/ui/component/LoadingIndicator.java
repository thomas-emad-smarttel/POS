package com.restaurantpos.desktop.ui.component;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;

/**
 * Reusable non-blocking loading indicator state view.
 */
public final class LoadingIndicator extends VBox {

    private final Label messageLabel;

    public LoadingIndicator(String message) {
        getStyleClass().add("pos-state-box");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(36, 36);
        this.messageLabel = new Label(message);
        this.messageLabel.getStyleClass().add("pos-text-muted");
        getChildren().addAll(spinner, messageLabel);
    }

    public void setMessage(String message) {
        messageLabel.setText(message);
    }
}
