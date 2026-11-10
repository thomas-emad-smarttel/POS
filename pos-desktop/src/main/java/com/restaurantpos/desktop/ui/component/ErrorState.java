package com.restaurantpos.desktop.ui.component;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Reusable error/offline fallback state with optional retry action.
 */
public final class ErrorState extends VBox {

    private final Label titleLabel;
    private final Label messageLabel;
    private final SecondaryButton retryButton;

    public ErrorState(String title, String message, Runnable onRetry) {
        getStyleClass().add("pos-state-box");
        this.titleLabel = new Label(title);
        this.titleLabel.getStyleClass().add("pos-heading-md");
        this.messageLabel = new Label(message);
        this.messageLabel.getStyleClass().add("pos-text-muted");
        this.messageLabel.setWrapText(true);
        this.retryButton = new SecondaryButton("Retry");
        if (onRetry != null) {
            this.retryButton.setOnAction(e -> onRetry.run());
        } else {
            this.retryButton.setVisible(false);
            this.retryButton.setManaged(false);
        }
        getChildren().addAll(titleLabel, messageLabel, retryButton);
    }

    public void update(String title, String message) {
        titleLabel.setText(title);
        messageLabel.setText(message);
    }
}
