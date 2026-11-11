package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.SelectBoxes;
import com.restaurantpos.desktop.ui.component.StatusBadge;
import com.restaurantpos.desktop.ui.component.ViewState;
import com.restaurantpos.desktop.ui.component.ViewStateContainer;
import com.restaurantpos.shared.dto.pos.DiningAreaDto;
import com.restaurantpos.shared.dto.pos.DiningTableDto;
import com.restaurantpos.shared.dto.pos.SaveDiningAreaRequest;
import com.restaurantpos.shared.dto.pos.SaveDiningTableRequest;
import com.restaurantpos.shared.dto.pos.UpdateTableStatusRequest;
import com.restaurantpos.shared.security.PermissionCode;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Branch dining areas and floor tables management screen.
 */
public final class TableManagementView extends VBox {

    private record FloorData(List<DiningAreaDto> areas, List<DiningTableDto> tables) {
    }

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final FlowPane tablesFlow = new FlowPane(12, 12);
    private final ComboBox<DiningAreaDto> areaCombo = new ComboBox<>();
    private final TextField areaCodeField = new TextField();
    private final TextField areaNameField = new TextField();
    private final TextField tableCodeField = new TextField();
    private final TextField tableNameField = new TextField();
    private final TextField capacityField = new TextField("4");
    private final ViewStateContainer stateContainer;

    public TableManagementView(
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

        tablesFlow.setPadding(new Insets(8));
        ScrollPane scroll = new ScrollPane(tablesFlow);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        this.stateContainer = new ViewStateContainer(scroll, this::loadData);
        VBox.setVgrow(stateContainer, Priority.ALWAYS);

        Label heading = new Label("Dining Areas & Tables");
        heading.getStyleClass().add("pos-heading-lg");
        SecondaryButton refreshBtn = new SecondaryButton("Refresh Floor");
        refreshBtn.setOnAction(e -> loadData());
        HBox header = new HBox(12, heading, refreshBtn);

        getChildren().addAll(header, banner);
        if (sessionContext.hasPermission(PermissionCode.TABLES_MANAGE)) {
            getChildren().add(buildAdminBar());
        }
        getChildren().add(stateContainer);

        loadData();
    }

    private VBox buildAdminBar() {
        areaCodeField.setPromptText("Area Code (MAIN)");
        areaCodeField.getStyleClass().add("pos-input");
        areaNameField.setPromptText("Area Name (Main Hall)");
        areaNameField.getStyleClass().add("pos-input");
        PrimaryButton addAreaBtn = new PrimaryButton("Add Area");
        addAreaBtn.setOnAction(e -> createArea());

        tableCodeField.setPromptText("Table Code (T1)");
        tableCodeField.getStyleClass().add("pos-input");
        tableNameField.setPromptText("Display Name (Table 1)");
        tableNameField.getStyleClass().add("pos-input");
        capacityField.setPrefWidth(65);
        capacityField.getStyleClass().add("pos-input");
        PrimaryButton addTableBtn = new PrimaryButton("Add Table");
        addTableBtn.setOnAction(e -> createTable());

        HBox areaRow = new HBox(8, areaCodeField, areaNameField, addAreaBtn);
        SelectBoxes.configure(areaCombo, "Select Dining Area...", a -> a.name() + " (" + a.code() + ")");
        HBox tableRow = new HBox(8, areaCombo, tableCodeField, tableNameField, capacityField, addTableBtn);
        VBox box = new VBox(8, areaRow, tableRow);
        box.getStyleClass().add("pos-card");
        return box;
    }

    public final void loadData() {
        banner.clear();
        stateContainer.setState(ViewState.LOADING);
        String token = sessionContext.accessToken();
        Task<FloorData> task = new Task<>() {
            @Override
            protected FloorData call() throws Exception {
                return new FloorData(
                        serverClient.listDiningAreas(token).data(),
                        serverClient.listDiningTables(token).data()
                );
            }
        };
        task.setOnSucceeded(e -> {
            FloorData data = task.getValue();
            areaCombo.getItems().setAll(data.areas());
            if (!data.areas().isEmpty()) {
                areaCombo.getSelectionModel().selectFirst();
            }
            renderTables(data.tables());
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void renderTables(List<DiningTableDto> tables) {
        tablesFlow.getChildren().clear();
        if (tables.isEmpty()) {
            stateContainer.setState(ViewState.EMPTY);
            return;
        }
        boolean canManage = sessionContext.hasPermission(PermissionCode.TABLES_MANAGE);
        for (DiningTableDto t : tables) {
            StatusBadge.Tone tone = switch (t.status()) {
                case "AVAILABLE" -> StatusBadge.Tone.SUCCESS;
                case "OCCUPIED" -> StatusBadge.Tone.DANGER;
                case "RESERVED", "CLEANING" -> StatusBadge.Tone.WARNING;
                default -> StatusBadge.Tone.NEUTRAL;
            };
            Label name = new Label(t.displayName() + " (" + t.tableCode() + ")");
            name.getStyleClass().add("pos-heading-md");
            Label meta = new Label(t.diningAreaName() + " • Cap " + t.capacity());
            meta.getStyleClass().add("pos-text-muted");
            StatusBadge badge = new StatusBadge(t.status(), tone);

            VBox card = new VBox(8, name, meta, badge);
            card.getStyleClass().add("pos-product-card");
            if (canManage) {
                SecondaryButton statusBtn = new SecondaryButton("Set Status");
                statusBtn.setOnAction(e -> promptStatusUpdate(t));
                card.getChildren().add(statusBtn);
            }
            tablesFlow.getChildren().add(card);
        }
        stateContainer.setState(ViewState.CONTENT);
    }

    private void createArea() {
        String code = areaCodeField.getText().trim();
        String name = areaNameField.getText().trim();
        if (code.isEmpty() || name.isEmpty()) {
            banner.showError("Area code and name are required.");
            return;
        }
        String token = sessionContext.accessToken();
        Task<DiningAreaDto> task = new Task<>() {
            @Override
            protected DiningAreaDto call() throws Exception {
                return serverClient.createDiningArea(token, new SaveDiningAreaRequest(code, name, 0, true)).data();
            }
        };
        task.setOnSucceeded(e -> {
            areaCodeField.clear();
            areaNameField.clear();
            banner.showSuccess("Created dining area " + task.getValue().name());
            loadData();
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void createTable() {
        DiningAreaDto area = areaCombo.getValue();
        String code = tableCodeField.getText().trim();
        String name = tableNameField.getText().trim();
        if (area == null || code.isEmpty() || name.isEmpty()) {
            banner.showError("Select an area and enter table code and name.");
            return;
        }
        int cap = Integer.parseInt(capacityField.getText().trim());
        String token = sessionContext.accessToken();
        Task<DiningTableDto> task = new Task<>() {
            @Override
            protected DiningTableDto call() throws Exception {
                return serverClient.createDiningTable(token,
                        new SaveDiningTableRequest(area.id(), code, name, cap, 0, true)).data();
            }
        };
        task.setOnSucceeded(e -> {
            tableCodeField.clear();
            tableNameField.clear();
            banner.showSuccess("Created table " + task.getValue().displayName());
            loadData();
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void promptStatusUpdate(DiningTableDto table) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>("AVAILABLE", "AVAILABLE", "RESERVED", "CLEANING", "OUT_OF_SERVICE");
        dialog.setTitle("Update Table Status");
        dialog.setHeaderText("Set status for " + table.displayName());
        dialog.showAndWait().ifPresent(status -> {
            String token = sessionContext.accessToken();
            Task<DiningTableDto> task = new Task<>() {
                @Override
                protected DiningTableDto call() throws Exception {
                    return serverClient.updateTableStatus(token, table.id(),
                            new UpdateTableStatusRequest(status, table.version())).data();
                }
            };
            task.setOnSucceeded(e -> loadData());
            task.setOnFailed(e -> handleFailure(task.getException()));
            ioExecutor.submit(task);
        });
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
        banner.showError(ex != null ? ex.getMessage() : "Table operation failed.");
    }
}
