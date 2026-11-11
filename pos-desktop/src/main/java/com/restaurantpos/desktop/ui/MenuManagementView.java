package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.SelectBoxes;
import com.restaurantpos.desktop.ui.component.ViewState;
import com.restaurantpos.desktop.ui.component.ViewStateContainer;
import com.restaurantpos.shared.dto.pos.BranchProductOverrideRequest;
import com.restaurantpos.shared.dto.pos.CategoryDto;
import com.restaurantpos.shared.dto.pos.ProductDto;
import com.restaurantpos.shared.dto.pos.SaveCategoryRequest;
import com.restaurantpos.shared.dto.pos.SaveProductRequest;
import com.restaurantpos.shared.security.PermissionCode;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Menu categories, products, and branch-specific availability/price override manager.
 */
public final class MenuManagementView extends VBox {

    private record MenuData(List<CategoryDto> categories, List<ProductDto> products) {
    }

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final TableView<ProductDto> table = new TableView<>();
    private final ComboBox<CategoryDto> categoryCombo = new ComboBox<>();
    private final TextField catCodeField = new TextField();
    private final TextField catNameField = new TextField();
    private final TextField skuField = new TextField();
    private final TextField prodNameField = new TextField();
    private final TextField priceField = new TextField();
    private final TextField imageRefField = new TextField();
    private final ViewStateContainer stateContainer;

    public MenuManagementView(
            LocalServerClient serverClient,
            SessionContext sessionContext,
            ExecutorService ioExecutor,
            Runnable onSessionExpired
    ) {
        this.serverClient = Objects.requireNonNull(serverClient);
        this.sessionContext = Objects.requireNonNull(sessionContext);
        this.ioExecutor = Objects.requireNonNull(ioExecutor);
        this.onSessionExpired = Objects.requireNonNull(onSessionExpired);

        setSpacing(12);
        setPadding(new Insets(4));

        configureTable();
        VBox tableBox = new VBox(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        this.stateContainer = new ViewStateContainer(tableBox, this::loadData);
        VBox.setVgrow(stateContainer, Priority.ALWAYS);

        Label heading = new Label("Menu Catalog & Branch Overrides");
        heading.getStyleClass().add("pos-heading-lg");
        SecondaryButton refreshBtn = new SecondaryButton("Refresh Menu");
        refreshBtn.setOnAction(e -> loadData());
        HBox header = new HBox(12, heading, refreshBtn);

        getChildren().addAll(header, banner);
        if (sessionContext.hasAnyPermission(PermissionCode.MENU_MANAGE, PermissionCode.MENU_CREATE)) {
            getChildren().add(buildCreateForms());
        }
        getChildren().add(stateContainer);

        loadData();
    }

    private void configureTable() {
        table.getStyleClass().add("pos-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ProductDto, String> skuCol = new TableColumn<>("SKU");
        skuCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().sku()));

        TableColumn<ProductDto, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));

        TableColumn<ProductDto, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().categoryName()));

        TableColumn<ProductDto, String> priceCol = new TableColumn<>("Branch Price");
        priceCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().effectivePrice().toPlainString()));

        TableColumn<ProductDto, String> availCol = new TableColumn<>("Branch Status");
        availCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().branchAvailable() ? "AVAILABLE" : "DISABLED"));

        TableColumn<ProductDto, Void> actionCol = new TableColumn<>("Branch Override");
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final SecondaryButton toggleBtn = new SecondaryButton("Toggle Availability");

            {
                toggleBtn.setOnAction(e -> toggleProductAvailability(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || !sessionContext.hasAnyPermission(PermissionCode.MENU_MANAGE, PermissionCode.MENU_UPDATE)) {
                    setGraphic(null);
                } else {
                    setGraphic(toggleBtn);
                }
            }
        });

        table.getColumns().addAll(List.of(skuCol, nameCol, catCol, priceCol, availCol, actionCol));
    }

    private VBox buildCreateForms() {
        catCodeField.setPromptText("Category Code");
        catCodeField.getStyleClass().add("pos-input");
        catNameField.setPromptText("Category Name");
        catNameField.getStyleClass().add("pos-input");
        PrimaryButton addCatBtn = new PrimaryButton("Add Category");
        addCatBtn.setOnAction(e -> createCategory());

        skuField.setPromptText("SKU");
        skuField.getStyleClass().add("pos-input");
        prodNameField.setPromptText("Product Name");
        prodNameField.getStyleClass().add("pos-input");
        priceField.setPromptText("Price");
        priceField.setPrefWidth(95);
        priceField.getStyleClass().add("pos-input");
        imageRefField.setPromptText("Image URL / path (optional)");
        imageRefField.getStyleClass().add("pos-input");
        SecondaryButton browseImageBtn = new SecondaryButton("Choose Image...");
        browseImageBtn.setOnAction(e -> chooseProductImage());
        PrimaryButton addProdBtn = new PrimaryButton("Add Product");
        addProdBtn.setOnAction(e -> createProduct());

        SelectBoxes.configure(categoryCombo, "Select Category...", c -> c.name() + " (" + c.code() + ")");
        HBox catRow = new HBox(8, catCodeField, catNameField, addCatBtn);
        HBox prodRow = new HBox(8, categoryCombo, skuField, prodNameField, priceField, imageRefField, browseImageBtn, addProdBtn);
        HBox.setHgrow(imageRefField, Priority.ALWAYS);
        VBox card = new VBox(8, catRow, prodRow);
        card.getStyleClass().add("pos-card");
        return card;
    }

    public final void loadData() {
        banner.clear();
        stateContainer.setState(ViewState.LOADING);
        String token = sessionContext.accessToken();
        Task<MenuData> task = new Task<>() {
            @Override
            protected MenuData call() throws Exception {
                return new MenuData(
                        serverClient.listMenuCategories(token).data(),
                        serverClient.searchMenuProducts(token, null, null, 0, 100).data()
                );
            }
        };
        task.setOnSucceeded(e -> {
            MenuData data = task.getValue();
            categoryCombo.getItems().setAll(data.categories());
            if (!data.categories().isEmpty()) {
                categoryCombo.getSelectionModel().selectFirst();
            }
            table.getItems().setAll(data.products());
            stateContainer.setState(data.products().isEmpty() ? ViewState.EMPTY : ViewState.CONTENT);
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void createCategory() {
        String code = catCodeField.getText().trim();
        String name = catNameField.getText().trim();
        if (code.isEmpty() || name.isEmpty()) {
            banner.showError("Category code and name are required.");
            return;
        }
        String token = sessionContext.accessToken();
        Task<CategoryDto> task = new Task<>() {
            @Override
            protected CategoryDto call() throws Exception {
                return serverClient.createCategory(token,
                        new SaveCategoryRequest(null, code, name, 0, null, true)).data();
            }
        };
        task.setOnSucceeded(e -> {
            catCodeField.clear();
            catNameField.clear();
            banner.showSuccess("Created category " + task.getValue().name());
            loadData();
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void createProduct() {
        CategoryDto cat = categoryCombo.getValue();
        String sku = skuField.getText().trim();
        String name = prodNameField.getText().trim();
        if (cat == null || sku.isEmpty() || name.isEmpty() || priceField.getText().isBlank()) {
            banner.showError("Select a category and enter SKU, name, and price.");
            return;
        }
        BigDecimal price = new BigDecimal(priceField.getText().trim());
        String imageRef = imageRefField.getText() != null && !imageRefField.getText().isBlank()
                ? imageRefField.getText().trim() : null;
        String token = sessionContext.accessToken();
        SaveProductRequest req = new SaveProductRequest(
                cat.id(), null, sku, null, name, null, price, "FIXED", "STANDARD", imageRef, 0, true, List.of()
        );
        Task<ProductDto> task = new Task<>() {
            @Override
            protected ProductDto call() throws Exception {
                return serverClient.createProduct(token, req).data();
            }
        };
        task.setOnSucceeded(e -> {
            skuField.clear();
            prodNameField.clear();
            priceField.clear();
            imageRefField.clear();
            banner.showSuccess("Created product " + task.getValue().name());
            loadData();
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void chooseProductImage() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select Product Image");
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif")
        );
        java.io.File chosen = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (chosen != null) {
            imageRefField.setText(chosen.toURI().toString());
        }
    }

    private void toggleProductAvailability(ProductDto product) {
        String token = sessionContext.accessToken();
        BranchProductOverrideRequest req = new BranchProductOverrideRequest(null, null, !product.branchAvailable());
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                serverClient.setBranchProductOverride(token, product.id(), req);
                return null;
            }
        };
        task.setOnSucceeded(e -> loadData());
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void handleFailure(Throwable ex) {
        if (ex instanceof ApiClientException ace) {
            if (ace.statusCode() == 401) {
                onSessionExpired.run();
                return;
            }
            if (ace.statusCode() == 403) {
                stateContainer.setState(ViewState.PERMISSION_DENIED);
                return;
            }
        }
        banner.showError(ex != null ? ex.getMessage() : "Menu operation failed.");
    }
}
