package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalCacheDatabase;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.CategoryButton;
import com.restaurantpos.desktop.ui.component.ConfirmDialog;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.OrderItemRow;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.ProductCard;
import com.restaurantpos.desktop.ui.component.SearchField;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.StatusBadge;
import com.restaurantpos.desktop.ui.component.ViewState;
import com.restaurantpos.desktop.ui.component.ViewStateContainer;
import com.restaurantpos.shared.dto.pos.AddOrderItemRequest;
import com.restaurantpos.shared.dto.pos.ApplyDiscountRequest;
import com.restaurantpos.shared.dto.pos.CancelOrderRequest;
import com.restaurantpos.shared.dto.pos.CategoryDto;
import com.restaurantpos.shared.dto.pos.CreateOrderRequest;
import com.restaurantpos.shared.dto.pos.DiningTableDto;
import com.restaurantpos.shared.dto.pos.OrderDto;
import com.restaurantpos.shared.dto.pos.OrderItemDto;
import com.restaurantpos.shared.dto.pos.OrderSummaryDto;
import com.restaurantpos.shared.dto.pos.ProductDto;
import com.restaurantpos.shared.dto.pos.TransferOrderRequest;
import com.restaurantpos.shared.dto.pos.UpdateOrderItemRequest;
import com.restaurantpos.shared.security.PermissionCode;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Main touch/keyboard-optimized JavaFX POS terminal screen backed by authoritative Local Server APIs
 * and read-only SQLite menu cache fallback when offline.
 */
public final class PosTerminalView extends BorderPane {

    private record CatalogPayload(List<CategoryDto> categories, List<ProductDto> products, boolean fromOfflineCache) {
    }

    private final LocalServerClient serverClient;
    private final LocalCacheDatabase localCache;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final StatusBadge modeBadge = new StatusBadge("ONLINE", StatusBadge.Tone.SUCCESS);
    private final VBox categoryRail = new VBox(8);
    private final FlowPane productGrid = new FlowPane(14, 14);
    private final SearchField searchField = new SearchField("Search menu or scan barcode [F2]...");
    private final ViewStateContainer gridStateContainer;

    private final ComboBox<String> orderTypeCombo = new ComboBox<>();
    private final Label orderHeaderLabel = new Label("No Active Order");
    private final StatusBadge orderStatusBadge = new StatusBadge("IDLE", StatusBadge.Tone.NEUTRAL);
    private final VBox cartItemsBox = new VBox(8);
    private final Label subtotalLabel = new Label("Subtotal: $0.00");
    private final Label discountLabel = new Label("Discount: $0.00");
    private final Label taxLabel = new Label("Tax: $0.00");
    private final Label grandTotalLabel = new Label("Total: $0.00");

    private final PrimaryButton sendKitchenBtn = new PrimaryButton("Send to Kitchen [F9]");
    private final PrimaryButton checkoutBtn = new PrimaryButton("Checkout & Pay");
    private final SecondaryButton holdBtn = new SecondaryButton("Hold [F8]");
    private final SecondaryButton discountBtn = new SecondaryButton("Discount");
    private final SecondaryButton transferBtn = new SecondaryButton("Transfer Table");
    private final SecondaryButton cancelOrderBtn = new SecondaryButton("Cancel");

    private UUID selectedCategoryId;
    private OrderDto activeOrder;
    private boolean offlineMode;

    public PosTerminalView(
            LocalServerClient serverClient,
            LocalCacheDatabase localCache,
            SessionContext sessionContext,
            ExecutorService ioExecutor,
            Runnable onSessionExpired
    ) {
        this.serverClient = Objects.requireNonNull(serverClient);
        this.localCache = localCache;
        this.sessionContext = Objects.requireNonNull(sessionContext);
        this.ioExecutor = Objects.requireNonNull(ioExecutor);
        this.onSessionExpired = Objects.requireNonNull(onSessionExpired);

        setPadding(new Insets(4));
        setTop(buildTopActionBar());
        setLeft(buildCategoryPanel());

        productGrid.setPadding(new Insets(4, 8, 12, 4));
        ScrollPane gridScroll = new ScrollPane(productGrid);
        gridScroll.setFitToWidth(true);
        gridScroll.getStyleClass().add("pos-scroll-pane");
        this.gridStateContainer = new ViewStateContainer(gridScroll, this::loadCatalog);
        setCenter(gridStateContainer);

        setRight(buildCartPanel());
        installKeyboardShortcuts();

        loadCatalog();
        refreshCartUi();
    }

    private VBox buildTopActionBar() {
        orderTypeCombo.getItems().setAll("COUNTER", "DINE_IN", "TAKEAWAY", "DELIVERY");
        orderTypeCombo.setValue("COUNTER");

        PrimaryButton newOrderBtn = new PrimaryButton("New Order [F4]");
        newOrderBtn.setOnAction(e -> startNewOrder());

        SecondaryButton tablesBtn = new SecondaryButton("Pick Table [F6]");
        tablesBtn.setOnAction(e -> selectTableForOrder());

        SecondaryButton heldOrdersBtn = new SecondaryButton("Held Orders");
        heldOrdersBtn.setOnAction(e -> retrieveHeldOrderDialog());

        PauseTransition debounce = new PauseTransition(Duration.millis(180));
        debounce.setOnFinished(e -> loadCatalog());
        searchField.textProperty().addListener((obs, o, n) -> debounce.playFromStart());
        searchField.setOnAction(e -> handleBarcodeOrSearchEnter());
        HBox.setHgrow(searchField, Priority.ALWAYS);

        HBox row = new HBox(10, searchField, orderTypeCombo, newOrderBtn, tablesBtn, heldOrdersBtn, modeBadge);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 0, 10, 0));

        return new VBox(6, banner, row);
    }

    private VBox buildCategoryPanel() {
        Label title = new Label("Categories");
        title.getStyleClass().add("pos-heading-md");

        ScrollPane scroll = new ScrollPane(categoryRail);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("pos-scroll-pane");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox panel = new VBox(10, title, scroll);
        panel.getStyleClass().add("pos-card");
        panel.setPrefWidth(190);
        BorderPane.setMargin(panel, new Insets(0, 12, 0, 0));
        return panel;
    }

    private VBox buildCartPanel() {
        orderHeaderLabel.getStyleClass().add("pos-heading-md");
        HBox header = new HBox(8, orderHeaderLabel, orderStatusBadge);
        header.setAlignment(Pos.CENTER_LEFT);

        ScrollPane itemsScroll = new ScrollPane(cartItemsBox);
        itemsScroll.setFitToWidth(true);
        itemsScroll.getStyleClass().add("pos-scroll-pane");
        VBox.setVgrow(itemsScroll, Priority.ALWAYS);

        grandTotalLabel.getStyleClass().add("pos-total-label");
        VBox totalsBox = new VBox(4, subtotalLabel, discountLabel, taxLabel, grandTotalLabel);
        totalsBox.getStyleClass().add("pos-module-card");

        holdBtn.setOnAction(e -> holdCurrentOrder());
        discountBtn.setOnAction(e -> applyDiscountPrompt());
        transferBtn.setOnAction(e -> transferTablePrompt());
        cancelOrderBtn.setOnAction(e -> cancelCurrentOrder());
        sendKitchenBtn.setOnAction(e -> sendCurrentOrderToKitchen());
        sendKitchenBtn.getStyleClass().setAll("button", "pos-btn-kitchen");
        checkoutBtn.setOnAction(e -> openCheckoutDialog());
        checkoutBtn.getStyleClass().setAll("button", "pos-btn-checkout");
        checkoutBtn.setMaxWidth(Double.MAX_VALUE);
        sendKitchenBtn.setMaxWidth(Double.MAX_VALUE);

        HBox row1 = new HBox(8, holdBtn, discountBtn);
        HBox row2 = new HBox(8, transferBtn, cancelOrderBtn);
        holdBtn.setMaxWidth(Double.MAX_VALUE);
        discountBtn.setMaxWidth(Double.MAX_VALUE);
        transferBtn.setMaxWidth(Double.MAX_VALUE);
        cancelOrderBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(holdBtn, Priority.ALWAYS);
        HBox.setHgrow(discountBtn, Priority.ALWAYS);
        HBox.setHgrow(transferBtn, Priority.ALWAYS);
        HBox.setHgrow(cancelOrderBtn, Priority.ALWAYS);

        VBox cart = new VBox(10, header, itemsScroll, totalsBox, row1, row2, sendKitchenBtn, checkoutBtn);
        cart.getStyleClass().add("pos-card");
        cart.setPrefWidth(365);
        BorderPane.setMargin(cart, new Insets(0, 0, 0, 12));
        return cart;
    }

    private void installKeyboardShortcuts() {
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F2) {
                searchField.requestFocus();
                event.consume();
            } else if (event.getCode() == KeyCode.F4) {
                startNewOrder();
                event.consume();
            } else if (event.getCode() == KeyCode.F6) {
                selectTableForOrder();
                event.consume();
            } else if (event.getCode() == KeyCode.F8) {
                holdCurrentOrder();
                event.consume();
            } else if (event.getCode() == KeyCode.F9) {
                sendCurrentOrderToKitchen();
                event.consume();
            }
        });
    }

    public void handleRealtimeEvent() {
        loadCatalog();
        if (activeOrder != null) {
            reloadActiveOrder(activeOrder.id());
        }
    }

    public final void loadCatalog() {
        gridStateContainer.setState(ViewState.LOADING);
        String token = sessionContext.accessToken();
        UUID branchId = sessionContext.profile() != null ? sessionContext.profile().activeBranchId() : null;
        String query = searchField.getText() != null ? searchField.getText().trim() : "";

        Task<CatalogPayload> task = new Task<>() {
            @Override
            protected CatalogPayload call() throws Exception {
                try {
                    List<CategoryDto> cats = serverClient.listMenuCategories(token).data();
                    List<ProductDto> prods = serverClient.searchMenuProducts(token, selectedCategoryId, query, 0, 40).data();
                    if (localCache != null && branchId != null && selectedCategoryId == null && query.isEmpty()) {
                        localCache.replaceBranchCategories(branchId, cats);
                        localCache.replaceBranchProducts(branchId, prods);
                    }
                    return new CatalogPayload(cats, prods, false);
                } catch (ApiClientException ace) {
                    throw ace;
                } catch (Exception networkEx) {
                    if (localCache != null && branchId != null) {
                        List<CategoryDto> cachedCats = localCache.readBranchCategories(branchId);
                        List<ProductDto> cachedProds = localCache.readBranchProducts(branchId).stream()
                                .filter(p -> selectedCategoryId == null || selectedCategoryId.equals(p.categoryId()))
                                .filter(p -> query.isEmpty()
                                        || p.name().toLowerCase().contains(query.toLowerCase())
                                        || p.sku().toLowerCase().contains(query.toLowerCase()))
                                .toList();
                        return new CatalogPayload(cachedCats, cachedProds, true);
                    }
                    throw networkEx;
                }
            }
        };

        task.setOnSucceeded(e -> {
            CatalogPayload payload = task.getValue();
            this.offlineMode = payload.fromOfflineCache();
            if (offlineMode) {
                modeBadge.setText("OFFLINE CACHE");
                modeBadge.setTone(StatusBadge.Tone.WARNING);
                banner.showError("Local Server unreachable — browsing read-only SQLite cached menu. Order actions disabled.");
            } else {
                modeBadge.setText("ONLINE");
                modeBadge.setTone(StatusBadge.Tone.SUCCESS);
            }
            renderCategories(payload.categories());
            renderProductGrid(payload.products());
            refreshCartUi();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            if (ex instanceof ApiClientException ace && ace.statusCode() == 401) {
                onSessionExpired.run();
                return;
            }
            gridStateContainer.showError(ex != null ? ex.getMessage() : "Unable to load menu catalog.");
        });

        ioExecutor.submit(task);
    }

    private void renderCategories(List<CategoryDto> categories) {
        categoryRail.getChildren().clear();
        CategoryButton allBtn = new CategoryButton("All Items");
        allBtn.setActive(selectedCategoryId == null);
        allBtn.setOnAction(e -> {
            selectedCategoryId = null;
            loadCatalog();
        });
        categoryRail.getChildren().add(allBtn);

        for (CategoryDto cat : categories) {
            if (!cat.active() || !cat.branchVisible()) {
                continue;
            }
            CategoryButton btn = new CategoryButton(cat.name());
            btn.setActive(cat.id().equals(selectedCategoryId));
            btn.setOnAction(e -> {
                selectedCategoryId = cat.id();
                loadCatalog();
            });
            categoryRail.getChildren().add(btn);
        }
    }

    private void renderProductGrid(List<ProductDto> products) {
        productGrid.getChildren().clear();
        if (products.isEmpty()) {
            gridStateContainer.setState(searchField.getText().isBlank() ? ViewState.EMPTY : ViewState.NO_RESULTS);
            return;
        }
        for (ProductDto product : products) {
            String currency = activeOrder != null ? activeOrder.currencyCode() : "USD";
            ProductCard card = new ProductCard(product, currency, () -> onProductClicked(product));
            productGrid.getChildren().add(card);
        }
        gridStateContainer.setState(ViewState.CONTENT);
    }

    private void handleBarcodeOrSearchEnter() {
        String text = searchField.getText() != null ? searchField.getText().trim() : "";
        if (text.isEmpty() || offlineMode) {
            loadCatalog();
            return;
        }
        String token = sessionContext.accessToken();
        Task<ProductDto> task = new Task<>() {
            @Override
            protected ProductDto call() throws Exception {
                return serverClient.findProductByBarcode(token, text).data();
            }
        };
        task.setOnSucceeded(e -> {
            searchField.clear();
            onProductClicked(task.getValue());
        });
        task.setOnFailed(e -> loadCatalog());
        ioExecutor.submit(task);
    }

    private void onProductClicked(ProductDto product) {
        if (offlineMode) {
            banner.showError("Cannot add items while Local Server is offline.");
            return;
        }
        if (activeOrder == null) {
            String orderType = orderTypeCombo.getValue() != null ? orderTypeCombo.getValue() : "COUNTER";
            if ("DINE_IN".equals(orderType)) {
                selectTableForOrder(product);
            } else {
                createOrderOnServer(orderType, null, null, product);
            }
            return;
        }
        promptModifiersAndAddItem(product);
    }

    private void promptModifiersAndAddItem(ProductDto product) {
        boolean hasChoices = (product.variants() != null && !product.variants().isEmpty())
                || (product.modifierGroups() != null && !product.modifierGroups().isEmpty());

        if (hasChoices) {
            Optional<ModifierSelectionDialog.SelectionResult> choice = ModifierSelectionDialog.show(product);
            choice.ifPresent(sel -> addItemToOrder(product.id(), sel.variantId(), sel.quantity(), sel.kitchenNotes(), sel.modifiers()));
        } else {
            addItemToOrder(product.id(), null, BigDecimal.ONE, null, List.of());
        }
    }

    private void startNewOrder() {
        if (offlineMode) {
            banner.showError("Cannot create orders while Local Server is offline.");
            return;
        }
        String orderType = orderTypeCombo.getValue();
        if ("DINE_IN".equals(orderType)) {
            selectTableForOrder(null);
            return;
        }
        createOrderOnServer(orderType, null, null, null);
    }

    private void selectTableForOrder() {
        selectTableForOrder(null);
    }

    private void selectTableForOrder(ProductDto pendingProduct) {
        if (offlineMode) {
            return;
        }
        String token = sessionContext.accessToken();
        Task<List<DiningTableDto>> task = new Task<>() {
            @Override
            protected List<DiningTableDto> call() throws Exception {
                return serverClient.listDiningTables(token).data();
            }
        };
        task.setOnSucceeded(e -> {
            List<DiningTableDto> tables = task.getValue();
            if (tables.isEmpty()) {
                banner.showError("No dining tables configured in this branch.");
                return;
            }
            ChoiceDialog<DiningTableDto> dialog = new ChoiceDialog<>(tables.get(0), tables);
            dialog.setTitle("Select Dining Table");
            dialog.setHeaderText("Choose an available table (or an occupied table to open its order)");
            dialog.showAndWait().ifPresent(table -> {
                if (table.activeOrderId() != null) {
                    reloadActiveOrder(table.activeOrderId(), pendingProduct);
                } else {
                    orderTypeCombo.setValue("DINE_IN");
                    createOrderOnServer("DINE_IN", table.id(), table.version(), pendingProduct);
                }
            });
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void createOrderOnServer(String orderType, UUID tableId, Long tableVersion, ProductDto pendingProduct) {
        banner.clear();
        String token = sessionContext.accessToken();
        CreateOrderRequest req = new CreateOrderRequest(orderType, tableId, tableVersion, null, 1, null, null);
        Task<OrderDto> task = new Task<>() {
            @Override
            protected OrderDto call() throws Exception {
                return serverClient.createOrder(token, req).data();
            }
        };
        task.setOnSucceeded(e -> {
            this.activeOrder = task.getValue();
            banner.showSuccess("Created order " + activeOrder.orderNumber());
            refreshCartUi();
            if (pendingProduct != null) {
                promptModifiersAndAddItem(pendingProduct);
            }
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void addItemToOrder(
            UUID productId,
            UUID variantId,
            BigDecimal qty,
            String notes,
            List<AddOrderItemRequest.SelectedModifierInput> modifiers
    ) {
        banner.clear();
        String token = sessionContext.accessToken();
        UUID orderId = activeOrder.id();
        AddOrderItemRequest req = new AddOrderItemRequest(productId, variantId, qty, notes, modifiers, activeOrder.version());
        Task<OrderDto> task = new Task<>() {
            @Override
            protected OrderDto call() throws Exception {
                return serverClient.addOrderItem(token, orderId, req).data();
            }
        };
        task.setOnSucceeded(e -> {
            this.activeOrder = task.getValue();
            refreshCartUi();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void adjustItemQty(OrderItemDto item, BigDecimal delta) {
        BigDecimal nextQty = item.quantity().add(delta);
        if (nextQty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String token = sessionContext.accessToken();
        UUID orderId = activeOrder.id();
        UpdateOrderItemRequest req = new UpdateOrderItemRequest(nextQty, item.kitchenNotes(), null, activeOrder.version());
        Task<OrderDto> task = new Task<>() {
            @Override
            protected OrderDto call() throws Exception {
                return serverClient.updateOrderItem(token, orderId, item.id(), req).data();
            }
        };
        task.setOnSucceeded(e -> {
            this.activeOrder = task.getValue();
            refreshCartUi();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void removeItem(OrderItemDto item) {
        String token = sessionContext.accessToken();
        UUID orderId = activeOrder.id();
        long ver = activeOrder.version();
        Task<OrderDto> task = new Task<>() {
            @Override
            protected OrderDto call() throws Exception {
                return serverClient.removeOrderItem(token, orderId, item.id(), ver).data();
            }
        };
        task.setOnSucceeded(e -> {
            this.activeOrder = task.getValue();
            refreshCartUi();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void applyDiscountPrompt() {
        if (activeOrder == null) return;
        TextInputDialog dialog = new TextInputDialog("10");
        dialog.setTitle("Apply Order Discount (%)");
        dialog.setHeaderText("Enter percentage discount (validated by server role limits)");
        dialog.showAndWait().ifPresent(raw -> {
            try {
                BigDecimal pct = new BigDecimal(raw.trim());
                String token = sessionContext.accessToken();
                ApplyDiscountRequest req = new ApplyDiscountRequest(
                        null, null, "POS Discount " + pct + "%", "PERCENTAGE", pct,
                        "POS terminal discount", null, activeOrder.version()
                );
                Task<OrderDto> task = new Task<>() {
                    @Override
                    protected OrderDto call() throws Exception {
                        return serverClient.applyOrderDiscount(token, activeOrder.id(), req).data();
                    }
                };
                task.setOnSucceeded(e -> {
                    this.activeOrder = task.getValue();
                    refreshCartUi();
                    banner.showSuccess("Discount applied.");
                });
                task.setOnFailed(e -> handleApiError(task.getException()));
                ioExecutor.submit(task);
            } catch (NumberFormatException ex) {
                banner.showError("Enter a valid numeric percentage.");
            }
        });
    }

    private void holdCurrentOrder() {
        if (activeOrder == null) return;
        String token = sessionContext.accessToken();
        Task<OrderDto> task = new Task<>() {
            @Override
            protected OrderDto call() throws Exception {
                return serverClient.holdOrder(token, activeOrder.id(), activeOrder.version()).data();
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Order " + activeOrder.orderNumber() + " placed on hold.");
            this.activeOrder = null;
            refreshCartUi();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void retrieveHeldOrderDialog() {
        String token = sessionContext.accessToken();
        Task<List<OrderSummaryDto>> task = new Task<>() {
            @Override
            protected List<OrderSummaryDto> call() throws Exception {
                return serverClient.searchOrders(token, "ON_HOLD", true, 0, 25).data();
            }
        };
        task.setOnSucceeded(e -> {
            List<OrderSummaryDto> held = task.getValue();
            if (held.isEmpty()) {
                banner.showSuccess("No orders currently on hold.");
                return;
            }
            ChoiceDialog<OrderSummaryDto> dialog = new ChoiceDialog<>(held.get(0), held);
            dialog.setTitle("Retrieve Held Order");
            dialog.setHeaderText("Select an order to resume");
            dialog.showAndWait().ifPresent(chosen -> {
                Task<OrderDto> resumeTask = new Task<>() {
                    @Override
                    protected OrderDto call() throws Exception {
                        return serverClient.retrieveHeldOrder(token, chosen.id(), chosen.version()).data();
                    }
                };
                resumeTask.setOnSucceeded(ev -> {
                    this.activeOrder = resumeTask.getValue();
                    refreshCartUi();
                });
                resumeTask.setOnFailed(ev -> handleApiError(resumeTask.getException()));
                ioExecutor.submit(resumeTask);
            });
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void transferTablePrompt() {
        if (activeOrder == null) return;
        String token = sessionContext.accessToken();
        Task<List<DiningTableDto>> task = new Task<>() {
            @Override
            protected List<DiningTableDto> call() throws Exception {
                return serverClient.listDiningTables(token).data().stream()
                        .filter(t -> "AVAILABLE".equals(t.status()))
                        .toList();
            }
        };
        task.setOnSucceeded(e -> {
            List<DiningTableDto> avail = task.getValue();
            if (avail.isEmpty()) {
                banner.showError("No available tables to transfer to.");
                return;
            }
            ChoiceDialog<DiningTableDto> dialog = new ChoiceDialog<>(avail.get(0), avail);
            dialog.setTitle("Transfer Order");
            dialog.setHeaderText("Select target table for " + activeOrder.orderNumber());
            dialog.showAndWait().ifPresent(target -> {
                Task<OrderDto> trTask = new Task<>() {
                    @Override
                    protected OrderDto call() throws Exception {
                        return serverClient.transferOrder(token, activeOrder.id(),
                                new TransferOrderRequest(target.id(), activeOrder.version(), target.version())).data();
                    }
                };
                trTask.setOnSucceeded(ev -> {
                    this.activeOrder = trTask.getValue();
                    refreshCartUi();
                    banner.showSuccess("Order transferred to " + target.displayName());
                });
                trTask.setOnFailed(ev -> handleApiError(trTask.getException()));
                ioExecutor.submit(trTask);
            });
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void sendCurrentOrderToKitchen() {
        if (activeOrder == null) return;
        String token = sessionContext.accessToken();
        Task<OrderDto> task = new Task<>() {
            @Override
            protected OrderDto call() throws Exception {
                return serverClient.sendOrderToKitchen(token, activeOrder.id(), activeOrder.version()).data();
            }
        };
        task.setOnSucceeded(e -> {
            this.activeOrder = task.getValue();
            refreshCartUi();
            banner.showSuccess("Order " + activeOrder.orderNumber() + " dispatched to kitchen.");
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void cancelCurrentOrder() {
        if (activeOrder == null) return;
        if (!ConfirmDialog.confirm("Cancel Order", "Cancel " + activeOrder.orderNumber() + "?",
                "This releases any attached dining table.")) {
            return;
        }
        String token = sessionContext.accessToken();
        Task<OrderDto> task = new Task<>() {
            @Override
            protected OrderDto call() throws Exception {
                return serverClient.cancelOrder(token, activeOrder.id(),
                        new CancelOrderRequest("Cancelled from POS terminal", null, activeOrder.version())).data();
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Order " + activeOrder.orderNumber() + " cancelled.");
            this.activeOrder = null;
            refreshCartUi();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void reloadActiveOrder(UUID orderId) {
        reloadActiveOrder(orderId, null);
    }

    private void reloadActiveOrder(UUID orderId, ProductDto pendingProduct) {
        String token = sessionContext.accessToken();
        Task<OrderDto> task = new Task<>() {
            @Override
            protected OrderDto call() throws Exception {
                return serverClient.getOrder(token, orderId).data();
            }
        };
        task.setOnSucceeded(e -> {
            this.activeOrder = task.getValue();
            refreshCartUi();
            if (pendingProduct != null) {
                promptModifiersAndAddItem(pendingProduct);
            }
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void refreshCartUi() {
        cartItemsBox.getChildren().clear();
        boolean hasOrder = activeOrder != null;
        boolean canEdit = hasOrder && !offlineMode && sessionContext.hasAnyPermission(PermissionCode.ORDERS_EDIT, PermissionCode.ORDERS_UPDATE);

        holdBtn.setDisable(!canEdit);
        discountBtn.setDisable(!canEdit || !sessionContext.hasAnyPermission(PermissionCode.ORDERS_DISCOUNT, PermissionCode.ORDERS_DISCOUNT_APPLY));
        transferBtn.setDisable(!canEdit || !sessionContext.hasPermission(PermissionCode.ORDERS_TRANSFER));
        cancelOrderBtn.setDisable(!hasOrder || offlineMode || !sessionContext.hasPermission(PermissionCode.ORDERS_CANCEL));
        sendKitchenBtn.setDisable(!canEdit || activeOrder.items().isEmpty());
        checkoutBtn.setDisable(!hasOrder || offlineMode || activeOrder.items().isEmpty());

        if (!hasOrder) {
            orderHeaderLabel.setText("No Active Order");
            orderStatusBadge.setText("IDLE");
            orderStatusBadge.setTone(StatusBadge.Tone.NEUTRAL);
            subtotalLabel.setText("Subtotal: $0.00");
            discountLabel.setText("Discount: $0.00");
            taxLabel.setText("Tax: $0.00");
            grandTotalLabel.setText("Total: $0.00");
            cartItemsBox.getChildren().add(buildCartEmptyCard(
                    "Start an Order",
                    "Tap any item on the menu to open a Counter order automatically, or click Pick Table [F6] for Dine-In."
            ));
            return;
        }

        orderHeaderLabel.setText(activeOrder.orderNumber() + " (" + activeOrder.orderType() + ")");
        orderStatusBadge.setText(activeOrder.status() + " v" + activeOrder.version());
        orderStatusBadge.setTone(StatusBadge.Tone.SUCCESS);

        String curr = activeOrder.currencyCode();
        if (activeOrder.items().isEmpty()) {
            cartItemsBox.getChildren().add(buildCartEmptyCard(
                    "Cart is Empty",
                    "Tap menu items on the left to add them. Once added, Send to Kitchen [F9] and Checkout & Pay become active."
            ));
        } else {
            for (OrderItemDto item : activeOrder.items()) {
                cartItemsBox.getChildren().add(new OrderItemRow(
                        item,
                        curr,
                        canEdit,
                        () -> adjustItemQty(item, BigDecimal.ONE),
                        () -> adjustItemQty(item, BigDecimal.ONE.negate()),
                        () -> removeItem(item)
                ));
            }
        }

        subtotalLabel.setText("Subtotal: " + curr + " " + fmt(activeOrder.subtotalAmount()));
        discountLabel.setText("Discount: -" + curr + " " + fmt(activeOrder.discountAmount()));
        taxLabel.setText("Tax: " + curr + " " + fmt(activeOrder.taxAmount()));
        grandTotalLabel.setText("Total: " + curr + " " + fmt(activeOrder.totalAmount()));
    }

    private static VBox buildCartEmptyCard(String title, String message) {
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("pos-heading-md");
        Label msgLbl = new Label(message);
        msgLbl.getStyleClass().add("pos-text-muted");
        msgLbl.setWrapText(true);
        msgLbl.setStyle("-fx-text-alignment: center;");
        VBox box = new VBox(6, titleLbl, msgLbl);
        box.getStyleClass().add("pos-cart-empty");
        return box;
    }

    private static String fmt(BigDecimal val) {
        return (val != null ? val : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void handleApiError(Throwable ex) {
        if (ex instanceof ApiClientException ace) {
            if (ace.statusCode() == 401) {
                onSessionExpired.run();
                return;
            }
            if (ace.statusCode() == 409 && activeOrder != null) {
                banner.showError("Concurrent edit conflict! Refreshing latest order state from server.");
                reloadActiveOrder(activeOrder.id());
                return;
            }
        }
        banner.showError(ex != null ? ex.getMessage() : "Request failed.");
    }

    private void openCheckoutDialog() {
        if (activeOrder == null || offlineMode) {
            return;
        }
        UUID openOrderId = activeOrder.id();
        CheckoutDialog dialog = new CheckoutDialog(
                serverClient,
                sessionContext,
                ioExecutor,
                activeOrder,
                () -> reloadActiveOrder(openOrderId),
                onSessionExpired
        );
        dialog.showAndWait();
        reloadActiveOrder(openOrderId);
    }
}
