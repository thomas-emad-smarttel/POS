package com.restaurantpos.desktop.ui.component;

import javafx.scene.control.Label;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formatted currency label adhering to 2-decimal display precision.
 */
public final class PriceLabel extends Label {

    public PriceLabel(BigDecimal amount, String currencyCode) {
        getStyleClass().add("pos-price-label");
        setAmount(amount, currencyCode);
    }

    public void setAmount(BigDecimal amount, String currencyCode) {
        BigDecimal safe = amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        String curr = currencyCode != null ? currencyCode : "USD";
        setText(curr + " " + safe.toPlainString());
    }
}
