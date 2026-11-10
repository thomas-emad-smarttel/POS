package com.restaurantpos.desktop.ui.component;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Reusable empty/no-results/permission-denied placeholder state view.
 */
public final class EmptyState extends VBox {

    public EmptyState(String title, String description) {
        getStyleClass().add("pos-state-box");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("pos-heading-md");
        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("pos-text-muted");
        descLabel.setWrapText(true);
        getChildren().addAll(titleLabel, descLabel);
    }
}
