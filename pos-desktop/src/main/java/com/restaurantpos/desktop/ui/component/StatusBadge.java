package com.restaurantpos.desktop.ui.component;

import javafx.scene.control.Label;

/**
 * Compact semantic status pill (Success, Warning, Danger, Neutral).
 */
public final class StatusBadge extends Label {

    public enum Tone {
        SUCCESS("pos-badge-success"),
        WARNING("pos-badge-warning"),
        DANGER("pos-badge-danger"),
        NEUTRAL("pos-badge-neutral");

        private final String cssClass;

        Tone(String cssClass) {
            this.cssClass = cssClass;
        }
    }

    public StatusBadge(String text, Tone tone) {
        super(text);
        getStyleClass().add("pos-badge");
        setTone(tone);
    }

    public void setTone(Tone tone) {
        getStyleClass().removeAll(
                Tone.SUCCESS.cssClass,
                Tone.WARNING.cssClass,
                Tone.DANGER.cssClass,
                Tone.NEUTRAL.cssClass
        );
        getStyleClass().add(tone.cssClass);
    }
}
