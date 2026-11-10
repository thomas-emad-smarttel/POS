package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.ViewState;
import com.restaurantpos.desktop.ui.component.ViewStateContainer;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.security.AuditLogDto;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Paginated security audit log viewer restricted to authorized roles ({@code audit.view}).
 */
public final class AuditLogView extends VBox {

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final TableView<AuditLogDto> table = new TableView<>();
    private final ViewStateContainer stateContainer;

    public AuditLogView(
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
        VBox content = new VBox(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        this.stateContainer = new ViewStateContainer(content, this::loadLogs);
        VBox.setVgrow(stateContainer, Priority.ALWAYS);

        Label heading = new Label("Security Audit Logs");
        heading.getStyleClass().add("pos-heading-lg");
        SecondaryButton refreshBtn = new SecondaryButton("Refresh Audit Logs");
        refreshBtn.setOnAction(e -> loadLogs());
        HBox header = new HBox(12, heading, refreshBtn);

        getChildren().addAll(header, stateContainer);
        loadLogs();
    }

    private void configureTable() {
        table.getStyleClass().add("pos-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<AuditLogDto, String> timeCol = new TableColumn<>("Occurred At");
        timeCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().occurredAt())));

        TableColumn<AuditLogDto, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().action()));

        TableColumn<AuditLogDto, String> targetCol = new TableColumn<>("Target");
        targetCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().targetEntityType() + ":" + c.getValue().targetEntityId()
        ));

        TableColumn<AuditLogDto, String> corrCol = new TableColumn<>("Correlation ID");
        corrCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().correlationId()));

        TableColumn<AuditLogDto, String> detailsCol = new TableColumn<>("Context");
        detailsCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().detailsJson() != null ? c.getValue().detailsJson() : ""
        ));

        table.getColumns().addAll(List.of(timeCol, actionCol, targetCol, corrCol, detailsCol));
    }

    public final void loadLogs() {
        stateContainer.setState(ViewState.LOADING);
        String token = sessionContext.accessToken();
        var branchId = sessionContext.profile() != null ? sessionContext.profile().activeBranchId() : null;
        Task<ApiResponse<List<AuditLogDto>>> task = new Task<>() {
            @Override
            protected ApiResponse<List<AuditLogDto>> call() throws Exception {
                return serverClient.listAuditLogs(token, branchId, 0, 50);
            }
        };
        task.setOnSucceeded(e -> {
            List<AuditLogDto> logs = task.getValue().data();
            table.getItems().setAll(logs);
            stateContainer.setState(logs.isEmpty() ? ViewState.EMPTY : ViewState.CONTENT);
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
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
            stateContainer.showError(ex != null ? ex.getMessage() : "Unable to load audit logs.");
        });
        ioExecutor.submit(task);
    }
}
