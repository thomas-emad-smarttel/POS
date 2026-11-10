package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.ConfirmDialog;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.ViewState;
import com.restaurantpos.desktop.ui.component.ViewStateContainer;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.security.SessionDto;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Active terminal session viewer and administrative session revocation screen.
 */
public final class SessionManagementView extends VBox {

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final TableView<SessionDto> table = new TableView<>();
    private final ViewStateContainer stateContainer;

    public SessionManagementView(
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
        VBox content = new VBox(12, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        this.stateContainer = new ViewStateContainer(content, this::loadSessions);
        VBox.setVgrow(stateContainer, Priority.ALWAYS);

        SecondaryButton refreshBtn = new SecondaryButton("Refresh Sessions");
        refreshBtn.setOnAction(e -> loadSessions());
        Label heading = new Label("Active & Recent Terminal Sessions");
        heading.getStyleClass().add("pos-heading-lg");
        HBox header = new HBox(12, heading, refreshBtn);

        getChildren().addAll(header, banner, stateContainer);
        loadSessions();
    }

    private void configureTable() {
        table.getStyleClass().add("pos-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<SessionDto, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().username() + (c.getValue().current() ? " (Current)" : "")));

        TableColumn<SessionDto, String> branchCol = new TableColumn<>("Branch");
        branchCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().branchId())));

        TableColumn<SessionDto, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().revokedAt() != null ? "REVOKED (" + c.getValue().revokedAt() + ")" : "ACTIVE until " + c.getValue().expiresAt()
        ));

        TableColumn<SessionDto, Void> actionCol = new TableColumn<>("Action");
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button revokeBtn = new SecondaryButton("Revoke");

            {
                revokeBtn.setOnAction(e -> {
                    SessionDto item = getTableView().getItems().get(getIndex());
                    if (ConfirmDialog.confirm(
                            "Revoke Session",
                            "Revoke session for " + item.username() + "?",
                            "The terminal using this session will immediately be signed out on its next request."
                    )) {
                        revokeSession(item);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    SessionDto row = getTableView().getItems().get(getIndex());
                    revokeBtn.setDisable(row.revokedAt() != null);
                    setGraphic(revokeBtn);
                }
            }
        });

        table.getColumns().addAll(List.of(userCol, branchCol, statusCol, actionCol));
    }

    public final void loadSessions() {
        banner.clear();
        stateContainer.setState(ViewState.LOADING);
        String token = sessionContext.accessToken();
        Task<ApiResponse<List<SessionDto>>> task = new Task<>() {
            @Override
            protected ApiResponse<List<SessionDto>> call() throws Exception {
                return serverClient.listSessions(token, 0, 50);
            }
        };
        task.setOnSucceeded(e -> {
            List<SessionDto> items = task.getValue().data();
            table.getItems().setAll(items);
            stateContainer.setState(items.isEmpty() ? ViewState.EMPTY : ViewState.CONTENT);
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
            stateContainer.showError(ex != null ? ex.getMessage() : "Unable to load sessions.");
        });
        ioExecutor.submit(task);
    }

    private void revokeSession(SessionDto session) {
        String token = sessionContext.accessToken();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                serverClient.revokeSession(token, session.id());
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Session revoked for " + session.username());
            if (session.current()) {
                onSessionExpired.run();
            } else {
                loadSessions();
            }
        });
        task.setOnFailed(e -> banner.showError(task.getException() != null ? task.getException().getMessage() : "Revocation failed."));
        ioExecutor.submit(task);
    }
}
