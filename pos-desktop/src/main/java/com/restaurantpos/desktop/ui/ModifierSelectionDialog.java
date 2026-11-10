package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.shared.dto.pos.AddOrderItemRequest;
import com.restaurantpos.shared.dto.pos.ModifierDto;
import com.restaurantpos.shared.dto.pos.ModifierGroupDto;
import com.restaurantpos.shared.dto.pos.ProductDto;
import com.restaurantpos.shared.dto.pos.ProductVariantDto;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Programmatic dialog for selecting product variants, required/optional modifier groups, quantity, and kitchen notes.
 */
public final class ModifierSelectionDialog {

    public record SelectionResult(
            UUID variantId,
            BigDecimal quantity,
            String kitchenNotes,
            List<AddOrderItemRequest.SelectedModifierInput> modifiers
    ) {
    }

    private ModifierSelectionDialog() {
    }

    public static Optional<SelectionResult> show(ProductDto product) {
        Dialog<SelectionResult> dialog = new Dialog<>();
        dialog.setTitle(product.name());
        dialog.setHeaderText("Configure " + product.name());

        ButtonType addBtnType = new ButtonType("Add to Order", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtnType, ButtonType.CANCEL);

        NotificationBanner banner = new NotificationBanner();
        VBox content = new VBox(12, banner);
        content.setPadding(new Insets(12));
        content.setPrefWidth(460);

        ComboBox<ProductVariantDto> variantCombo = new ComboBox<>();
        if (product.variants() != null && !product.variants().isEmpty()) {
            variantCombo.getItems().setAll(product.variants().stream().filter(ProductVariantDto::active).toList());
            variantCombo.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(ProductVariantDto v) {
                    return v == null ? "" : v.name() + " (" + v.priceOverride() + ")";
                }

                @Override
                public ProductVariantDto fromString(String string) {
                    return null;
                }
            });
            if (!variantCombo.getItems().isEmpty()) {
                variantCombo.getSelectionModel().selectFirst();
            }
            content.getChildren().addAll(new Label("Size / Variant"), variantCombo);
        }

        List<GroupControl> groupControls = new ArrayList<>();
        if (product.modifierGroups() != null) {
            for (ModifierGroupDto group : product.modifierGroups()) {
                if (!group.active()) {
                    continue;
                }
                VBox groupBox = new VBox(6);
                groupBox.setStyle("-fx-background-color: #EEF2F6; -fx-padding: 10; -fx-background-radius: 6;");
                String reqText = group.required() || group.minSelections() > 0 ? " (Required)" : " (Optional)";
                Label groupLabel = new Label(group.name() + reqText + " [min " + group.minSelections() + ", max " + group.maxSelections() + "]");
                groupLabel.setStyle("-fx-font-weight: bold;");
                groupBox.getChildren().add(groupLabel);

                if (group.maxSelections() == 1) {
                    ToggleGroup tg = new ToggleGroup();
                    List<RadioButton> radios = new ArrayList<>();
                    for (ModifierDto m : group.modifiers()) {
                        if (!m.active()) continue;
                        RadioButton rb = new RadioButton(m.name() + (m.priceDelta().signum() > 0 ? " (+" + m.priceDelta() + ")" : ""));
                        rb.setUserData(m);
                        rb.setToggleGroup(tg);
                        if (m.defaultSelected() && tg.getSelectedToggle() == null) {
                            rb.setSelected(true);
                        }
                        radios.add(rb);
                        groupBox.getChildren().add(rb);
                    }
                    groupControls.add(new GroupControl(group, radios, List.of()));
                } else {
                    List<CheckBox> checks = new ArrayList<>();
                    for (ModifierDto m : group.modifiers()) {
                        if (!m.active()) continue;
                        CheckBox cb = new CheckBox(m.name() + (m.priceDelta().signum() > 0 ? " (+" + m.priceDelta() + ")" : ""));
                        cb.setUserData(m);
                        cb.setSelected(m.defaultSelected());
                        checks.add(cb);
                        groupBox.getChildren().add(cb);
                    }
                    groupControls.add(new GroupControl(group, List.of(), checks));
                }
                content.getChildren().add(groupBox);
            }
        }

        TextField qtyField = new TextField("1");
        TextField notesField = new TextField();
        notesField.setPromptText("Special kitchen instructions (e.g. No onions)");
        content.getChildren().addAll(new Label("Quantity"), qtyField, new Label("Kitchen Notes"), notesField);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(420);
        dialog.getDialogPane().setContent(scroll);

        final javafx.scene.Node okButton = dialog.getDialogPane().lookupButton(addBtnType);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                BigDecimal qty = new BigDecimal(qtyField.getText().trim());
                if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                    banner.showError("Quantity must be greater than zero.");
                    event.consume();
                    return;
                }
                for (GroupControl gc : groupControls) {
                    int selected = gc.selectedCount();
                    int min = Math.max(gc.group().required() ? 1 : 0, gc.group().minSelections());
                    if (selected < min || selected > gc.group().maxSelections()) {
                        banner.showError("Group '" + gc.group().name() + "' requires " + min + " to " + gc.group().maxSelections() + " selection(s).");
                        event.consume();
                        return;
                    }
                }
            } catch (NumberFormatException ex) {
                banner.showError("Enter a valid numeric quantity.");
                event.consume();
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn != addBtnType) {
                return null;
            }
            List<AddOrderItemRequest.SelectedModifierInput> selectedMods = new ArrayList<>();
            for (GroupControl gc : groupControls) {
                selectedMods.addAll(gc.selectedModifiers());
            }
            ProductVariantDto variant = variantCombo.getValue();
            return new SelectionResult(
                    variant != null ? variant.id() : null,
                    new BigDecimal(qtyField.getText().trim()),
                    notesField.getText().isBlank() ? null : notesField.getText().trim(),
                    selectedMods
            );
        });

        return dialog.showAndWait();
    }

    private record GroupControl(ModifierGroupDto group, List<RadioButton> radios, List<CheckBox> checks) {
        int selectedCount() {
            int count = 0;
            for (RadioButton r : radios) if (r.isSelected()) count++;
            for (CheckBox c : checks) if (c.isSelected()) count++;
            return count;
        }

        List<AddOrderItemRequest.SelectedModifierInput> selectedModifiers() {
            List<AddOrderItemRequest.SelectedModifierInput> list = new ArrayList<>();
            for (RadioButton r : radios) {
                if (r.isSelected() && r.getUserData() instanceof ModifierDto m) {
                    list.add(new AddOrderItemRequest.SelectedModifierInput(m.id(), 1));
                }
            }
            for (CheckBox c : checks) {
                if (c.isSelected() && c.getUserData() instanceof ModifierDto m) {
                    list.add(new AddOrderItemRequest.SelectedModifierInput(m.id(), 1));
                }
            }
            return list;
        }
    }
}
