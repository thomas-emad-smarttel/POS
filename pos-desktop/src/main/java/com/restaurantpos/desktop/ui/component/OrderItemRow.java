package com.restaurantpos.desktop.ui.component;

import com.restaurantpos.shared.dto.pos.OrderItemDto;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * Reusable cart line item showing product snapshot, modifiers, kitchen notes, and quick quantity controls.
 */
public final class OrderItemRow extends VBox {

    public OrderItemRow(
            OrderItemDto item,
            String currencyCode,
            boolean editable,
            Runnable onIncrement,
            Runnable onDecrement,
            Runnable onRemove
    ) {
        setSpacing(4);
        getStyleClass().add("pos-cart-item");

        String title = item.productNameSnapshot()
                + (item.variantNameSnapshot() != null ? " (" + item.variantNameSnapshot() + ")" : "");
        Label nameLabel = new Label(title);
        nameLabel.setStyle("-fx-font-weight: bold;");
        nameLabel.setWrapText(true);

        PriceLabel linePrice = new PriceLabel(item.lineTotal(), currencyCode);
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox topRow = new HBox(8, nameLabel, topSpacer, linePrice);
        topRow.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(topRow);

        if (item.modifiers() != null && !item.modifiers().isEmpty()) {
            String modSummary = item.modifiers().stream()
                    .map(m -> "+ " + m.modifierNameSnapshot())
                    .collect(Collectors.joining(", "));
            Label modLabel = new Label(modSummary);
            modLabel.getStyleClass().add("pos-text-muted");
            modLabel.setWrapText(true);
            getChildren().add(modLabel);
        }

        if (item.kitchenNotes() != null && !item.kitchenNotes().isBlank()) {
            Label notesLabel = new Label("Note: " + item.kitchenNotes());
            notesLabel.getStyleClass().add("pos-text-muted");
            notesLabel.setWrapText(true);
            getChildren().add(notesLabel);
        }

        Button minusBtn = new Button("-");
        minusBtn.getStyleClass().add("pos-qty-btn");
        minusBtn.setOnAction(e -> onDecrement.run());

        Label qtyLabel = new Label(item.quantity().stripTrailingZeros().toPlainString());
        qtyLabel.setStyle("-fx-font-weight: bold; -fx-padding: 0 6 0 6;");

        Button plusBtn = new Button("+");
        plusBtn.getStyleClass().add("pos-qty-btn");
        plusBtn.setOnAction(e -> onIncrement.run());

        Button removeBtn = new Button("Remove");
        removeBtn.getStyleClass().add("pos-qty-btn");
        removeBtn.setOnAction(e -> onRemove.run());

        boolean canEditLine = editable && !item.sentToKitchen();
        minusBtn.setDisable(!canEditLine || item.quantity().compareTo(BigDecimal.ONE) <= 0);
        plusBtn.setDisable(!canEditLine);
        removeBtn.setDisable(!canEditLine);

        Region ctrlSpacer = new Region();
        HBox.setHgrow(ctrlSpacer, Priority.ALWAYS);
        HBox controls = new HBox(6, minusBtn, qtyLabel, plusBtn, ctrlSpacer);
        if (item.sentToKitchen()) {
            controls.getChildren().add(new StatusBadge("SENT", StatusBadge.Tone.WARNING));
        } else {
            controls.getChildren().add(removeBtn);
        }
        controls.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(controls);
    }
}
