package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.StatusBadge;
import com.restaurantpos.shared.dto.kitchen.ExpoOrderDto;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketDto;
import com.restaurantpos.shared.security.PermissionCode;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Kitchen Expo / Pass aggregation screen grouping station tickets by order and enforcing
 * complete multi-station readiness before passing or serving the order.
 */
public final class KitchenExpoView extends VBox {

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final FlowPane ordersFlow = new FlowPane(12, 12);

    public KitchenExpoView(
            LocalServerClient serverClient,
            SessionContext sessionContext,
            ExecutorService ioExecutor,
            Runnable onSessionExpired
    ) {
        this.serverClient = serverClient;
        this.sessionContext = sessionContext;
        this.ioExecutor = ioExecutor;
        this.onSessionExpired = onSessionExpired;

        setSpacing(12);
        setPadding(new Insets(4));

        Label heading = new Label("Kitchen Expo / Pass Board");
        heading.getStyleClass().add("pos-heading-lg");
        SecondaryButton refreshBtn = new SecondaryButton("Refresh Expo");
        refreshBtn.setOnAction(e -> refreshExpo());
        HBox top = new HBox(12, heading, refreshBtn);
        top.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(ordersFlow);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(top, banner, scroll);
        refreshExpo();
    }

    public void refreshExpo() {
        String token = sessionContext.accessToken();
        Task<List<ExpoOrderDto>> task = new Task<>() {
            @Override
            protected List<ExpoOrderDto> call() throws Exception {
                return serverClient.getExpoOrders(token).data();
            }
        };
        task.setOnSucceeded(e -> renderOrders(task.getValue()));
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void renderOrders(List<ExpoOrderDto> orders) {
        ordersFlow.getChildren().clear();
        if (orders.isEmpty()) {
            ordersFlow.getChildren().add(new Label("No active kitchen orders awaiting pass."));
            return;
        }
        boolean canServe = sessionContext.hasAnyPermission(PermissionCode.KITCHEN_TICKET_SERVE, PermissionCode.KITCHEN_UPDATE);
        for (ExpoOrderDto order : orders) {
            VBox card = new VBox(8);
            card.getStyleClass().add("pos-card");
            card.setPrefWidth(330);
            if (order.allStationsReady()) {
                card.setStyle("-fx-border-color: #16A34A; -fx-border-width: 2;");
            } else if (order.anyOverdue()) {
                card.setStyle("-fx-border-color: #DC2626; -fx-border-width: 2;");
            }

            Label orderLbl = new Label(order.orderNumber()
                    + (order.tableLabel() != null ? " (Table " + order.tableLabel() + ")" : " (" + order.orderType() + ")"));
            orderLbl.getStyleClass().add("pos-heading-md");

            StatusBadge badge = new StatusBadge(
                    order.aggregateStatus() + " " + order.readyTicketCount() + "/" + order.totalTicketCount(),
                    order.allStationsReady() ? StatusBadge.Tone.SUCCESS : StatusBadge.Tone.WARNING
            );
            HBox head = new HBox(8, orderLbl, badge);
            head.setAlignment(Pos.CENTER_LEFT);

            VBox stationsList = new VBox(4);
            for (KitchenTicketDto st : order.stationTickets()) {
                Label stLbl = new Label("• " + st.stationName() + " [" + st.status() + "] — " + st.ticketNumber());
                stationsList.getChildren().add(stLbl);
            }

            Label pendingLbl = new Label(order.pendingStations().isEmpty()
                    ? "All stations READY for pass!"
                    : "Waiting on: " + String.join(", ", order.pendingStations()));
            pendingLbl.setWrapText(true);

            PrimaryButton serveBtn = new PrimaryButton("Serve Complete Order");
            serveBtn.setMaxWidth(Double.MAX_VALUE);
            serveBtn.setDisable(!canServe || !order.allStationsReady());
            serveBtn.setOnAction(e -> serveExpoOrder(order.orderId()));

            card.getChildren().addAll(head, stationsList, pendingLbl, serveBtn);
            ordersFlow.getChildren().add(card);
        }
    }

    private void serveExpoOrder(UUID orderId) {
        String token = sessionContext.accessToken();
        Task<List<KitchenTicketDto>> task = new Task<>() {
            @Override
            protected List<KitchenTicketDto> call() throws Exception {
                return serverClient.serveExpoOrder(token, orderId).data();
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Order marked SERVED across all stations.");
            refreshExpo();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void handleApiError(Throwable ex) {
        if (ex instanceof ApiClientException ace && ace.statusCode() == 401) {
            onSessionExpired.run();
            return;
        }
        banner.showError(ex != null ? ex.getMessage() : "Expo request failed.");
    }
}
