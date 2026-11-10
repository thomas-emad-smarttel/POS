package com.restaurantpos.desktop.ui.component;

import javafx.scene.control.Button;

/**
 * Touch-friendly category filter button for the POS terminal left rail.
 */
public final class CategoryButton extends Button {

    public CategoryButton(String text) {
        super(text);
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("pos-category-btn");
    }

    public void setActive(boolean active) {
        getStyleClass().remove("pos-category-btn-active");
        if (active) {
            getStyleClass().add("pos-category-btn-active");
        }
    }
}
