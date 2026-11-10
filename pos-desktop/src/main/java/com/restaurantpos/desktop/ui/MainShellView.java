package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.LocalCacheDatabase;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.PosWebSocketClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.StatusBadge;
import com.restaurantpos.desktop.ui.component.ViewState;
import com.restaurantpos.desktop.ui.component.ViewStateContainer;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.ServerStatusDto;
import com.restaurantpos.shared.dto.security.UserProfileDto;
import com.restaurantpos.shared.security.PermissionCode;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Authenticated programmatic JavaFX workspace shell with permission-aware navigation and WebSocket realtime sync.
 */
public final class MainShellView extends BorderPane {

    private final LocalServerClient serverClient;
    private final LocalCacheDatabase localCache;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final PosWebSocketClient webSocketClient;
    private final Runnable onLogout;

    private final StatusBadge connectionBadge = new StatusBadge("Checking Server...", StatusBadge.Tone.NEUTRAL);
    private final StatusBadge userBadge = new StatusBadge("Signed In", StatusBadge.Tone.NEUTRAL);
    private final Label serverInfoLabel = new Label("Waiting for branch Local Server status...");
    private final Label memoryUsageLabel = new Label();
    private final VBox navRail = new VBox(6);
    private final StackPane contentHost = new StackPane();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();

    private Node currentView;

    public MainShellView(
            LocalServerClient serverClient,
            LocalCacheDatabase localCache,
            String serverUrl,
            SessionContext sessionContext,
            ExecutorService ioExecutor,
            Runnable onLogout
    ) {
        this.serverClient = Objects.requireNonNull(serverClient);
        this.localCache = localCache;
        this.sessionContext = Objects.requireNonNull(sessionContext);
        this.ioExecutor = Objects.requireNonNull(ioExecutor);
        this.webSocketClient = new PosWebSocketClient(serverUrl);
        this.onLogout = Objects.requireNonNull(onLogout);

        this.serverInfoLabel.getStyleClass().add("pos-text-muted");
        this.memoryUsageLabel.getStyleClass().add("pos-text-muted");

        setTop(buildTopBar());
        setLeft(navRail);
        contentHost.setPadding(new Insets(16));
        setCenter(contentHost);

        rebuildNavigation();
        if (sessionContext.hasAnyPermission(PermissionCode.ORDERS_CREATE, PermissionCode.ORDERS_VIEW)) {
            activateDefaultPosView();
        } else {
            showDashboard();
        }
        updateMemoryReadout();
        connectRealtimeEvents();
    }

    private HBox buildTopBar() {
        Label brandLabel = new Label("Restaurant POS Terminal");
        brandLabel.getStyleClass().add("pos-heading-md");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        SecondaryButton logoutBtn = new SecondaryButton("Sign Out");
        logoutBtn.setOnAction(e -> performLogout());

        refreshUserBadge();
        HBox topBar = new HBox(12, brandLabel, spacer, userBadge, connectionBadge, logoutBtn);
        topBar.getStyleClass().add("pos-topbar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        return topBar;
    }

    private void refreshUserBadge() {
        UserProfileDto profile = sessionContext.profile();
        if (profile != null) {
            String roleSummary = (profile.assignedRoles() != null && !profile.assignedRoles().isEmpty())
                    ? String.join(", ", profile.assignedRoles())
                    : profile.username();
            userBadge.setText(profile.fullName() + " • " + roleSummary);
            userBadge.setTone(StatusBadge.Tone.SUCCESS);
            if (profile.activeBranchId() != null) {
                userBadge.setTooltip(new javafx.scene.control.Tooltip(
                        "Branch ID: " + profile.activeBranchId()
                                + (profile.terminalId() != null ? "\nTerminal ID: " + profile.terminalId() : "")
                ));
            }
        }
    }

    private void rebuildNavigation() {
        navRail.getChildren().clear();
        navButtons.clear();
        navRail.getStyleClass().add("pos-sidebar");

        Label sectionLabel = new Label("POS WORKSPACE");
        sectionLabel.getStyleClass().add("pos-sidebar-title");
        navRail.getChildren().add(sectionLabel);

        if (sessionContext.hasAnyPermission(PermissionCode.ORDERS_CREATE, PermissionCode.ORDERS_VIEW)) {
            addNavItem("pos", "POS Terminal", () -> new PosTerminalView(
                    serverClient, localCache, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
        }
        if (sessionContext.hasPermission(PermissionCode.TABLES_VIEW)) {
            addNavItem("tables", "Tables & Floor", () -> new TableManagementView(
                    serverClient, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
        }
        if (sessionContext.hasPermission(PermissionCode.KITCHEN_VIEW)) {
            addNavItem("kds", "Kitchen Display", () -> new KitchenDisplayView(
                    serverClient, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
        }
        if (sessionContext.hasPermission(PermissionCode.KITCHEN_EXPO_VIEW)) {
            addNavItem("expo", "Kitchen Expo", () -> new KitchenExpoView(
                    serverClient, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
        }
        if (sessionContext.hasPermission(PermissionCode.MENU_VIEW)) {
            addNavItem("menu", "Menu Catalog", () -> new MenuManagementView(
                    serverClient, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
        }
        if (sessionContext.hasPermission(PermissionCode.INVENTORY_VIEW)) {
            addNavItem("inventory_dash", "Inventory & Alerts", () -> new InventoryDashboardView(
                    serverClient, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
            addNavItem("inventory_ops", "Stock Operations", () -> new StockOperationsView(
                    serverClient, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
        }
        if (sessionContext.hasAnyPermission(PermissionCode.INVENTORY_ITEM_MANAGE, PermissionCode.INVENTORY_MANAGE)) {
            addNavItem("inventory_cat", "Inventory Setup", () -> new InventoryCatalogView(
                    serverClient, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
        }
        if (sessionContext.hasAnyPermission(PermissionCode.INVENTORY_RECIPE_VIEW, PermissionCode.INVENTORY_RECIPE_MANAGE)) {
            addNavItem("recipes", "Recipes & Costing", () -> new RecipeEditorView(
                    serverClient, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
        }
        if (sessionContext.hasAnyPermission(
                PermissionCode.CASH_SESSION_OPEN, PermissionCode.CASH_SESSION_CLOSE,
                PermissionCode.REPORTS_CASH_VIEW, PermissionCode.CASH_MOVEMENT_CREATE)) {
            addNavItem("cash", "Cash & Reconciliation", () -> new CashManagementView(
                    serverClient, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
        }

        addNavItem("dashboard", "System Health", this::buildDashboardNode);
        addNavItem("profile", "My Profile", () -> new ProfileView(
                serverClient, sessionContext, ioExecutor, this::onProfileUpdated, this::shutdownAndLogout
        ));

        if (sessionContext.hasAnyPermission(PermissionCode.USERS_VIEW, PermissionCode.USERS_MANAGE)) {
            addNavItem("users", "Users & Access", () -> new UserManagementView(
                    serverClient, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
        }
        if (sessionContext.hasAnyPermission(PermissionCode.ROLES_VIEW, PermissionCode.ROLES_MANAGE)) {
            addNavItem("roles", "Roles & Matrix", () -> new RolePermissionMatrixView(
                    serverClient, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
        }
        if (sessionContext.hasPermission(PermissionCode.AUDIT_VIEW)) {
            addNavItem("sessions", "Active Sessions", () -> new SessionManagementView(
                    serverClient, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
            addNavItem("audit", "Audit Logs", () -> new AuditLogView(
                    serverClient, sessionContext, ioExecutor, this::shutdownAndLogout
            ));
        }
    }

    private void addNavItem(String key, String label, Supplier<Node> viewFactory) {
        Button navBtn = new Button(label);
        navBtn.setMaxWidth(Double.MAX_VALUE);
        navBtn.getStyleClass().add("pos-nav-item");
        navBtn.setOnAction(e -> activateNav(key, viewFactory.get()));
        navButtons.put(key, navBtn);
        navRail.getChildren().add(navBtn);
    }

    private void activateNav(String activeKey, Node view) {
        navButtons.forEach((key, btn) -> {
            btn.getStyleClass().remove("pos-nav-item-active");
            if (key.equals(activeKey)) {
                btn.getStyleClass().add("pos-nav-item-active");
            }
        });
        this.currentView = view;
        contentHost.getChildren().setAll(view);
    }

    private void activateDefaultPosView() {
        activateNav("pos", new PosTerminalView(
                serverClient, localCache, sessionContext, ioExecutor, this::shutdownAndLogout
        ));
    }

    private void showDashboard() {
        activateNav("dashboard", buildDashboardNode());
    }

    private void connectRealtimeEvents() {
        String token = sessionContext.accessToken();
        if (token == null) {
            return;
        }
        webSocketClient.connect(
                token,
                event -> Platform.runLater(() -> {
                    if (currentView instanceof PosTerminalView posView) {
                        posView.handleRealtimeEvent();
                    } else if (currentView instanceof TableManagementView tableView) {
                        tableView.loadData();
                    } else if (currentView instanceof MenuManagementView menuView) {
                        menuView.loadData();
                    } else if (currentView instanceof KitchenDisplayView kdsView) {
                        kdsView.handleRealtimeEvent();
                    } else if (currentView instanceof KitchenExpoView expoView) {
                        expoView.refreshExpo();
                    } else if (currentView instanceof InventoryDashboardView invDash) {
                        invDash.loadData();
                    } else if (currentView instanceof StockOperationsView opsView) {
                        opsView.loadData();
                    }
                }),
                () -> catchUpMissedEvents(token)
        );
    }

    private void catchUpMissedEvents(String token) {
        ioExecutor.submit(() -> {
            try {
                var missed = serverClient.fetchRecentEvents(token, webSocketClient.lastSequence()).data();
                if (missed != null && !missed.isEmpty()) {
                    Platform.runLater(() -> {
                        if (currentView instanceof PosTerminalView posView) {
                            posView.handleRealtimeEvent();
                        } else if (currentView instanceof KitchenDisplayView kdsView) {
                            kdsView.handleRealtimeEvent();
                        } else if (currentView instanceof KitchenExpoView expoView) {
                            expoView.refreshExpo();
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    private Node buildDashboardNode() {
        Label title = new Label("Branch Terminal Dashboard");
        title.getStyleClass().add("pos-heading-lg");

        Label subtitle = new Label(
                "Phase 4 Core POS active. Navigation reflects your server-resolved effective permissions; "
                        + "all authorization and pricing are enforced by the Local Server."
        );
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("pos-text-muted");

        PrimaryButton pingButton = new PrimaryButton("Check Local Server Health");
        VBox card = new VBox(14, title, subtitle, serverInfoLabel, memoryUsageLabel, pingButton);
        card.getStyleClass().add("pos-card");
        card.setMaxWidth(780);

        var containerRef = new java.util.concurrent.atomic.AtomicReference<ViewStateContainer>();
        ViewStateContainer container = new ViewStateContainer(card, () -> checkLocalServerHealth(containerRef.get()));
        containerRef.set(container);
        pingButton.setOnAction(event -> checkLocalServerHealth(container));
        checkLocalServerHealth(container);
        return container;
    }

    private void checkLocalServerHealth(ViewStateContainer container) {
        container.setState(ViewState.LOADING);
        connectionBadge.setText("Connecting...");
        connectionBadge.setTone(StatusBadge.Tone.WARNING);

        Task<ApiResponse<ServerStatusDto>> task = new Task<>() {
            @Override
            protected ApiResponse<ServerStatusDto> call() throws Exception {
                return serverClient.fetchHealth();
            }
        };

        task.setOnSucceeded(event -> {
            ApiResponse<ServerStatusDto> response = task.getValue();
            if (response != null && response.success() && response.data() != null) {
                ServerStatusDto status = response.data();
                connectionBadge.setText("Local Server Online");
                connectionBadge.setTone(StatusBadge.Tone.SUCCESS);
                serverInfoLabel.setText(String.format(
                        "Connected to %s v%s (%s) — Server Time: %s",
                        status.serviceName(),
                        status.version(),
                        status.operatingMode(),
                        status.serverTime()
                ));
                container.setState(ViewState.CONTENT);
            } else {
                connectionBadge.setText("Server Error");
                connectionBadge.setTone(StatusBadge.Tone.DANGER);
                container.showError("Unexpected health payload received from Local Server.");
            }
            updateMemoryReadout();
        });

        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            connectionBadge.setText("Offline Mode");
            connectionBadge.setTone(StatusBadge.Tone.DANGER);
            String reason = ex != null ? ex.getMessage() : "Connection refused";
            container.showOffline("Local Server unreachable on configured URL. (" + reason + ")");
            updateMemoryReadout();
        });

        ioExecutor.submit(task);
    }

    private void onProfileUpdated() {
        refreshUserBadge();
        rebuildNavigation();
    }

    private void performLogout() {
        String token = sessionContext.accessToken();
        if (token == null) {
            shutdownAndLogout();
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                serverClient.logout(token);
                return null;
            }
        };
        task.setOnSucceeded(e -> shutdownAndLogout());
        task.setOnFailed(e -> shutdownAndLogout());
        ioExecutor.submit(task);
    }

    private void shutdownAndLogout() {
        webSocketClient.close();
        onLogout.run();
    }

    private void updateMemoryReadout() {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        memoryUsageLabel.setText("Terminal JVM Heap: " + usedMb + " MB used / " + maxMb + " MB max");
    }
}
