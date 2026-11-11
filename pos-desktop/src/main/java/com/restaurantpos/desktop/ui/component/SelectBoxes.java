package com.restaurantpos.desktop.ui.component;

import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Shared formatting helpers for {@link ComboBox}, {@link ListView}, and {@link ChoiceDialog} controls
 * so Java record DTOs always render human-readable labels rather than raw {@code Record[...]} strings.
 */
public final class SelectBoxes {

    private SelectBoxes() {
    }

    public static <T> void configure(ComboBox<T> combo, Function<T, String> labelFn) {
        configure(combo, null, labelFn);
    }

    public static <T> void configure(ComboBox<T> combo, String promptText, Function<T, String> labelFn) {
        Objects.requireNonNull(combo);
        Objects.requireNonNull(labelFn);

        if (promptText != null && !promptText.isBlank()) {
            combo.setPromptText(promptText);
        }
        if (!combo.getStyleClass().contains("pos-select")) {
            combo.getStyleClass().add("pos-select");
        }

        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(T item) {
                return item == null ? (promptText != null ? promptText : "") : safeLabel(labelFn, item);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        });

        combo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else if (item == null) {
                    setText(promptText != null ? promptText : "");
                } else {
                    setText(safeLabel(labelFn, item));
                }
            }
        });

        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(promptText != null ? promptText : "");
                } else {
                    setText(safeLabel(labelFn, item));
                }
            }
        });
    }

    public static <T> void configureList(ListView<T> listView, Function<T, String> labelFn) {
        Objects.requireNonNull(listView);
        Objects.requireNonNull(labelFn);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : safeLabel(labelFn, item));
            }
        });
    }

    public static <T> ChoiceDialog<Option<T>> choiceDialog(
            List<T> items,
            T defaultValue,
            Function<T, String> labelFn
    ) {
        List<Option<T>> options = items.stream()
                .map(item -> new Option<>(item, safeLabel(labelFn, item)))
                .toList();
        Option<T> initial = options.stream()
                .filter(o -> Objects.equals(o.value(), defaultValue))
                .findFirst()
                .orElse(options.isEmpty() ? null : options.getFirst());
        return new ChoiceDialog<>(initial, options);
    }

    private static <T> String safeLabel(Function<T, String> labelFn, T item) {
        String label = labelFn.apply(item);
        return label != null ? label : "";
    }

    public record Option<T>(T value, String label) {
        @Override
        public String toString() {
            return label != null ? label : "";
        }
    }
}
