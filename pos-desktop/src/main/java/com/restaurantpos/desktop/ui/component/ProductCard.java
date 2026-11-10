package com.restaurantpos.desktop.ui.component;

import com.restaurantpos.shared.dto.pos.ProductDto;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Lightweight, touch-friendly menu item card for the POS product grid.
 */
public final class ProductCard extends VBox {

    public ProductCard(ProductDto product, String currencyCode, Runnable onSelect) {
        setSpacing(6);
        getStyleClass().add("pos-product-card");

        Label nameLabel = new Label(product.name());
        nameLabel.getStyleClass().add("pos-heading-md");
        nameLabel.setWrapText(true);
        nameLabel.setMaxHeight(42);

        Label skuLabel = new Label(product.sku() + (product.variants() != null && !product.variants().isEmpty()
                ? " • " + product.variants().size() + " sizes" : ""));
        skuLabel.getStyleClass().add("pos-text-muted");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        PriceLabel priceLabel = new PriceLabel(product.effectivePrice(), currencyCode);
        StatusBadge badge = new StatusBadge(
                product.branchAvailable() ? "IN STOCK" : "UNAVAILABLE",
                product.branchAvailable() ? StatusBadge.Tone.SUCCESS : StatusBadge.Tone.DANGER
        );

        HBox footer = new HBox(6, priceLabel, badge);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(nameLabel, skuLabel, spacer, footer);

        if (product.branchAvailable() && onSelect != null) {
            setOnMouseClicked(e -> onSelect.run());
        } else {
            getStyleClass().add("pos-product-card-disabled");
        }
    }
}
