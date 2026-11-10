package com.restaurantpos.desktop.ui.component;

import com.restaurantpos.shared.dto.pos.ProductDto;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;

import java.io.InputStream;
import java.net.URL;

/**
 * Touch-friendly menu item card with bounded cover-cropped product photo (or monogram fallback),
 * SKU/variant summary, price, and non-truncating availability badge.
 */
public final class ProductCard extends VBox {

    private static final double CARD_WIDTH = 214;
    private static final double IMAGE_WIDTH = 212;
    private static final double IMAGE_HEIGHT = 118;

    public ProductCard(ProductDto product, String currencyCode, Runnable onSelect) {
        setSpacing(0);
        setMinWidth(CARD_WIDTH);
        setPrefWidth(CARD_WIDTH);
        setMaxWidth(CARD_WIDTH);
        getStyleClass().add("pos-product-card");

        StackPane mediaHeader = buildMediaHeader(product);

        Label nameLabel = new Label(product.name());
        nameLabel.getStyleClass().add("pos-heading-md");
        nameLabel.setWrapText(true);
        nameLabel.setMinHeight(38);
        nameLabel.setMaxHeight(42);

        String subtitle = product.sku();
        if (product.variants() != null && !product.variants().isEmpty()) {
            subtitle += " • " + product.variants().size() + " sizes";
        } else if (product.modifierGroups() != null && !product.modifierGroups().isEmpty()) {
            subtitle += " • Customizable";
        }
        Label skuLabel = new Label(subtitle);
        skuLabel.getStyleClass().add("pos-text-muted");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        PriceLabel priceLabel = new PriceLabel(product.effectivePrice(), currencyCode);
        priceLabel.setMinWidth(Region.USE_PREF_SIZE);

        StatusBadge badge = new StatusBadge(
                product.branchAvailable() ? "IN STOCK" : "UNAVAILABLE",
                product.branchAvailable() ? StatusBadge.Tone.SUCCESS : StatusBadge.Tone.DANGER
        );
        badge.setMinWidth(Region.USE_PREF_SIZE);

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);

        HBox footer = new HBox(6, priceLabel, footerSpacer, badge);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(5, nameLabel, skuLabel, spacer, footer);
        body.getStyleClass().add("pos-product-card-body");
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(mediaHeader, body);

        if (product.description() != null && !product.description().isBlank()) {
            Tooltip.install(this, new Tooltip(product.description()));
        }

        if (product.branchAvailable() && onSelect != null) {
            setOnMouseClicked(e -> onSelect.run());
        } else {
            getStyleClass().add("pos-product-card-disabled");
            Tooltip.install(this, new Tooltip("Disabled in this branch — re-enable in Menu Catalog"));
        }
    }

    private StackPane buildMediaHeader(ProductDto product) {
        StackPane container = new StackPane();
        container.setMinSize(IMAGE_WIDTH, IMAGE_HEIGHT);
        container.setPrefSize(IMAGE_WIDTH, IMAGE_HEIGHT);
        container.setMaxSize(IMAGE_WIDTH, IMAGE_HEIGHT);
        container.getStyleClass().add("pos-product-image-placeholder");

        double r = 11;
        Path clip = new Path(
                new MoveTo(0, r),
                new QuadCurveTo(0, 0, r, 0),
                new LineTo(IMAGE_WIDTH - r, 0),
                new QuadCurveTo(IMAGE_WIDTH, 0, IMAGE_WIDTH, r),
                new LineTo(IMAGE_WIDTH, IMAGE_HEIGHT),
                new LineTo(0, IMAGE_HEIGHT),
                new ClosePath()
        );
        clip.setFill(javafx.scene.paint.Color.BLACK);
        container.setClip(clip);

        Image image = loadProductImage(product.imageRef());
        if (image != null && !image.isError()) {
            ImageView imageView = new ImageView(image);
            imageView.setManaged(false);
            imageView.setPreserveRatio(false);
            imageView.setSmooth(true);
            imageView.setFitWidth(IMAGE_WIDTH);
            imageView.setFitHeight(IMAGE_HEIGHT);
            applyCoverViewport(imageView, image);
            container.getChildren().add(imageView);
        } else {
            Label monogram = new Label(buildMonogram(product.name()));
            monogram.getStyleClass().add("pos-product-image-monogram");
            container.getChildren().add(monogram);
        }
        return container;
    }

    private static void applyCoverViewport(ImageView imageView, Image image) {
        if (image.getWidth() > 0 && image.getHeight() > 0) {
            updateViewportRect(imageView, image.getWidth(), image.getHeight());
        } else {
            image.progressProperty().addListener((obs, o, progress) -> {
                if (progress.doubleValue() >= 1.0 && image.getWidth() > 0 && image.getHeight() > 0) {
                    updateViewportRect(imageView, image.getWidth(), image.getHeight());
                }
            });
        }
    }

    private static void updateViewportRect(ImageView imageView, double srcW, double srcH) {
        double targetRatio = IMAGE_WIDTH / IMAGE_HEIGHT;
        double srcRatio = srcW / srcH;
        if (Math.abs(srcRatio - targetRatio) < 0.01) {
            return;
        }
        if (srcRatio > targetRatio) {
            double cropW = srcH * targetRatio;
            double x = (srcW - cropW) / 2.0;
            imageView.setViewport(new Rectangle2D(x, 0, cropW, srcH));
        } else {
            double cropH = srcW / targetRatio;
            double y = (srcH - cropH) / 2.0;
            imageView.setViewport(new Rectangle2D(0, y, srcW, cropH));
        }
    }

    private static Image loadProductImage(String imageRef) {
        if (imageRef == null || imageRef.isBlank()) {
            return null;
        }
        String trimmed = imageRef.trim();
        try {
            if (trimmed.startsWith("classpath:")) {
                String resPath = trimmed.substring("classpath:".length());
                if (!resPath.startsWith("/")) {
                    resPath = "/" + resPath;
                }
                URL resUrl = ProductCard.class.getResource(resPath);
                if (resUrl != null) {
                    try (InputStream in = resUrl.openStream()) {
                        return new Image(in, IMAGE_WIDTH * 2, IMAGE_HEIGHT * 2, true, true);
                    }
                }
                return null;
            }
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("file:")) {
                return new Image(trimmed, IMAGE_WIDTH * 2, IMAGE_HEIGHT * 2, true, true, true);
            }
            String normalized = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
            URL resUrl = ProductCard.class.getResource(normalized);
            if (resUrl != null) {
                try (InputStream in = resUrl.openStream()) {
                    return new Image(in, IMAGE_WIDTH * 2, IMAGE_HEIGHT * 2, true, true);
                }
            }
            return new Image(java.nio.file.Path.of(trimmed).toUri().toString(),
                    IMAGE_WIDTH * 2, IMAGE_HEIGHT * 2, true, true, false);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String buildMonogram(String name) {
        if (name == null || name.isBlank()) {
            return "POS";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
    }
}
