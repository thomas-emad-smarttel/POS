package com.restaurantpos.desktop.ui.component;

import javafx.scene.control.TextField;

/**
 * Debounce-friendly search and barcode input control for the POS product grid.
 */
public final class SearchField extends TextField {

    public SearchField(String prompt) {
        setPromptText(prompt);
        getStyleClass().add("pos-input");
    }
}
