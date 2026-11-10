package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.StatusBadge;
import com.restaurantpos.shared.dto.kitchen.KitchenPrintJobDto;
import com.restaurantpos.shared.dto.kitchen.KitchenStationDto;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketActionRequest;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketDto;
import com.restaurantpos.shared.dto.kitchen.KitchenTicketItemDto;
import com.restaurantpos.shared.dto.kitchen.KitchenUnroutedItemDto;
import com.restaurantpos.shared.dto.kitchen.ResolveUnroutedItemRequest;
import com.restaurantpos.shared.dto.kitchen.UpdateKitchenItemStatusRequest;
import com.restaurantpos.shared.security.PermissionCode;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Lightweight, touch/keyboard-friendly Kitchen Display System (KDS) view showing station ticket cards,
 * individual item readiness controls, elapsed/overdue indicators, unrouted item resolution, and print retries.
 */
public final class KitchenDisplayView extends VBox {

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final ComboBox<KitchenStationDto> stationCombo = new ComboBox<>();
    private final ComboBox<String> sortCombo = new ComboBox<>();
    private final StatusBadge syncBadge = new StatusBadge("Synced", StatusBadge.Tone.SUCCESS);
    private final HBox alertBar = new HBox(8);
    private final FlowPane ticketsFlow = new FlowPane(12, 12);

    private KitchenStationDto selectedStation;

    public KitchenDisplayView(
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

        sortCombo.getItems().addAll("FIFO", "PRIORITY", "OVERDUE_FIRST");
        sortCombo.setValue("FIFO");
        sortCombo.setOnAction(e -> refreshTickets());

        stationCombo.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(KitchenStationDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.name() + " [" + item.code() + "]");
            }
        });
        stationCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(KitchenStationDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.name() + " [" + item.code() + "]");
            }
        });
        stationCombo.setOnAction(e -> {
            selectedStation = stationCombo.getValue();
            refreshTickets();
        });

        Label title = new Label("Kitchen Display System (KDS)");
        title.getStyleClass().add("pos-heading-lg");

        SecondaryButton refreshBtn = new SecondaryButton("Refresh [F5]");
        refreshBtn.setOnAction(e -> refreshAll());

        HBox topBar = new HBox(10, title, new Label("Station:"), stationCombo, new Label("Sort:"), sortCombo,
                refreshBtn, syncBadge);
        topBar.setAlignment(Pos.CENTER_LEFT);

        alertBar.setAlignment(Pos.CENTER_LEFT);
        alertBar.setVisible(false);
        alertBar.setManaged(false);

        ScrollPane scroll = new ScrollPane(ticketsFlow);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.F5) {
                refreshAll();
                ev.consume();
            }
        });

        getChildren().addAll(topBar, banner, alertBar, scroll);
        refreshAll();
    }

    public void handleRealtimeEvent() {
        refreshTickets();
    }

    public void refreshAll() {
        String token = sessionContext.accessToken();
        Task<List<KitchenStationDto>> task = new Task<>() {
            @Override
            protected List<KitchenStationDto> call() throws Exception {
                return serverClient.listKitchenStations(token).data();
            }
        };
        task.setOnSucceeded(e -> {
            List<KitchenStationDto> stations = task.getValue().stream().filter(KitchenStationDto::active).toList();
            stationCombo.getItems().setAll(stations);
            if (!stations.isEmpty()) {
                if (selectedStation != null) {
                    stationCombo.setValue(stations.stream()
                            .filter(s -> s.id().equals(selectedStation.id()))
                            .findFirst().orElse(stations.getFirst()));
                } else {
                    stationCombo.setValue(stations.getFirst());
                }
                selectedStation = stationCombo.getValue();
                refreshTickets();
            } else {
                ticketsFlow.getChildren().setAll(new Label("No active kitchen stations configured for this branch."));
            }
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    public void refreshTickets() {
        if (selectedStation == null) {
            return;
        }
        String token = sessionContext.accessToken();
        UUID stationId = selectedStation.id();
        String sort = sortCombo.getValue();

        Task<KdsLoadResult> task = new Task<>() {
            @Override
            protected KdsLoadResult call() throws Exception {
                List<KitchenTicketDto> tickets = serverClient.listStationTickets(token, stationId, sort).data();
                List<KitchenUnroutedItemDto> unrouted = serverClient.listOpenUnroutedItems(token).data();
                List<KitchenPrintJobDto> failedPrints = serverClient.listFailedKitchenPrintJobs(token).data();
                return new KdsLoadResult(tickets, unrouted, failedPrints);
            }
        };
        task.setOnSucceeded(e -> {
            KdsLoadResult res = task.getValue();
            syncBadge.setText("Live (" + res.tickets().size() + ")");
            syncBadge.setTone(StatusBadge.Tone.SUCCESS);
            renderAlerts(res.unrouted(), res.failedPrints());
            renderTickets(res.tickets());
        });
        task.setOnFailed(e -> {
            syncBadge.setText("Disconnected");
            syncBadge.setTone(StatusBadge.Tone.DANGER);
            handleApiError(task.getException());
        });
        ioExecutor.submit(task);
    }

    private void renderAlerts(List<KitchenUnroutedItemDto> unrouted, List<KitchenPrintJobDto> failedPrints) {
        alertBar.getChildren().clear();
        if (!unrouted.isEmpty() && selectedStation != null
                && sessionContext.hasAnyPermission(PermissionCode.KITCHEN_ROUTING_MANAGE, PermissionCode.KITCHEN_MANAGE)) {
            KitchenUnroutedItemDto first = unrouted.getFirst();
            SecondaryButton routeBtn = new SecondaryButton("Route '" + first.productNameSnapshot() + "' -> " + selectedStation.code());
            routeBtn.setOnAction(e -> resolveUnrouted(first.id(), selectedStation.id()));
            alertBar.getChildren().addAll(
                    new StatusBadge(unrouted.size() + " Unrouted Item(s)", StatusBadge.Tone.WARNING),
                    routeBtn
            );
        }
        if (!failedPrints.isEmpty()
                && sessionContext.hasPermission(PermissionCode.KITCHEN_PRINTER_MANAGE)) {
            KitchenPrintJobDto job = failedPrints.getFirst();
            SecondaryButton retryPrintBtn = new SecondaryButton("Retry Print (" + failedPrints.size() + ")");
            retryPrintBtn.setOnAction(e -> retryPrint(job.id()));
            alertBar.getChildren().add(retryPrintBtn);
        }
        boolean hasAlerts = !alertBar.getChildren().isEmpty();
        alertBar.setVisible(hasAlerts);
        alertBar.setManaged(hasAlerts);
    }

    private void renderTickets(List<KitchenTicketDto> tickets) {
        ticketsFlow.getChildren().clear();
        if (tickets.isEmpty()) {
            ticketsFlow.getChildren().add(new Label("No active tickets for " + selectedStation.name()));
            return;
        }
        for (KitchenTicketDto ticket : tickets) {
            ticketsFlow.getChildren().add(buildTicketCard(ticket));
        }
    }

    private VBox buildTicketCard(KitchenTicketDto t) {
        VBox card = new VBox(8);
        card.getStyleClass().add("pos-card");
        card.setPrefWidth(305);
        if (t.overdue()) {
            card.setStyle("-fx-border-color: #DC2626; -fx-border-width: 2;");
        } else if ("READY".equals(t.status())) {
            card.setStyle("-fx-border-color: #16A34A; -fx-border-width: 2;");
        }

        Label ticketNumLbl = new Label(t.ticketNumber());
        ticketNumLbl.getStyleClass().add("pos-heading-md");

        StatusBadge.Tone statusTone = switch (t.status()) {
            case "READY" -> StatusBadge.Tone.SUCCESS;
            case "ON_HOLD" -> StatusBadge.Tone.WARNING;
            case "NEW", "QUEUED" -> StatusBadge.Tone.NEUTRAL;
            default -> StatusBadge.Tone.WARNING;
        };
        StatusBadge stBadge = new StatusBadge(t.status() + " v" + t.version(), statusTone);
        HBox head = new HBox(8, ticketNumLbl, stBadge);
        head.setAlignment(Pos.CENTER_LEFT);

        String tablePart = t.tableLabelSnapshot() != null ? " | Table " + t.tableLabelSnapshot() : "";
        String kindPart = !"INITIAL".equals(t.ticketKind()) ? " [" + t.ticketKind() + "]" : "";
        Label metaLbl = new Label(t.orderNumberSnapshot() + " | " + t.orderTypeSnapshot() + tablePart + kindPart);
        metaLbl.setWrapText(true);

        String elapsedText = formatElapsed(t.elapsedSeconds()) + " / " + t.expectedPrepMinutes() + "m"
                + (t.overdue() ? " OVERDUE" : "")
                + (!"NORMAL".equals(t.priorityLevel()) ? " | " + t.priorityLevel() : "");
        Label timerLbl = new Label(elapsedText);
        timerLbl.getStyleClass().add(t.overdue() ? "pos-text-danger" : "pos-text-muted");

        VBox itemsBox = new VBox(4);
        boolean canReadyItem = sessionContext.hasAnyPermission(PermissionCode.KITCHEN_TICKET_READY, PermissionCode.KITCHEN_UPDATE);
        for (KitchenTicketItemDto item : t.items()) {
            String qty = item.quantity().stripTrailingZeros().toPlainString();
            String mods = item.selectedModifiers().isEmpty() ? "" : "\n  + " + String.join(", ", item.selectedModifiers());
            String note = item.notes() != null && !item.notes().isBlank() ? "\n  [NOTE] " + item.notes() : "";
            Label itemLbl = new Label(qty + "x " + item.itemNameSnapshot() + " [" + item.status() + "]" + mods + note);
            itemLbl.setWrapText(true);
            HBox iRow = new HBox(6, itemLbl);
            iRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(itemLbl, Priority.ALWAYS);
            if (canReadyItem && !"READY".equals(item.status()) && !"CANCELLED".equals(item.status()) && !"SERVED".equals(item.status())) {
                SecondaryButton itemReadyBtn = new SecondaryButton("Ready");
                itemReadyBtn.setOnAction(e -> runItemAction(t.id(), item.id(), "READY", t.version()));
                iRow.getChildren().add(itemReadyBtn);
            }
            itemsBox.getChildren().add(iRow);
        }

        HBox actions1 = new HBox(6);
        if (("NEW".equals(t.status()) || "QUEUED".equals(t.status()))
                && sessionContext.hasAnyPermission(PermissionCode.KITCHEN_TICKET_ACCEPT, PermissionCode.KITCHEN_UPDATE)) {
            SecondaryButton accBtn = new SecondaryButton("Accept");
            accBtn.setOnAction(e -> runTicketAction(t.id(), "accept", t.version(), null));
            actions1.getChildren().add(accBtn);
        }
        if (("NEW".equals(t.status()) || "QUEUED".equals(t.status()) || "ACCEPTED".equals(t.status()))
                && sessionContext.hasAnyPermission(PermissionCode.KITCHEN_TICKET_START, PermissionCode.KITCHEN_UPDATE)) {
            PrimaryButton startBtn = new PrimaryButton("Start");
            startBtn.setOnAction(e -> runTicketAction(t.id(), "start", t.version(), null));
            actions1.getChildren().add(startBtn);
        }
        if ((!"READY".equals(t.status()) && !"ON_HOLD".equals(t.status()))
                && sessionContext.hasAnyPermission(PermissionCode.KITCHEN_TICKET_READY, PermissionCode.KITCHEN_UPDATE)) {
            PrimaryButton readyBtn = new PrimaryButton("Ready");
            readyBtn.setOnAction(e -> runTicketAction(t.id(), "ready", t.version(), null));
            actions1.getChildren().add(readyBtn);
        }
        if ("READY".equals(t.status())
                && sessionContext.hasAnyPermission(PermissionCode.KITCHEN_TICKET_SERVE, PermissionCode.KITCHEN_UPDATE)) {
            PrimaryButton serveBtn = new PrimaryButton("Serve");
            serveBtn.setOnAction(e -> runTicketAction(t.id(), "serve", t.version(), null));
            actions1.getChildren().add(serveBtn);
        }

        HBox actions2 = new HBox(6);
        if (sessionContext.hasPermission(PermissionCode.KITCHEN_TICKET_HOLD)) {
            if ("ON_HOLD".equals(t.status())) {
                SecondaryButton relBtn = new SecondaryButton("Release");
                relBtn.setOnAction(e -> runTicketAction(t.id(), "release", t.version(), "Released"));
                actions2.getChildren().add(relBtn);
            } else if (!"READY".equals(t.status())) {
                SecondaryButton holdBtn = new SecondaryButton("Hold");
                holdBtn.setOnAction(e -> promptReasonAndRun("Hold Ticket", "Station hold reason:", r ->
                        runTicketAction(t.id(), "hold", t.version(), r)));
                actions2.getChildren().add(holdBtn);
            }
        }
        if (sessionContext.hasPermission(PermissionCode.KITCHEN_TICKET_REFIRE)) {
            SecondaryButton refireBtn = new SecondaryButton("Re-fire");
            refireBtn.setOnAction(e -> promptReasonAndRun("Re-fire Ticket", "Re-fire reason:", r ->
                    runTicketAction(t.id(), "refire", t.version(), r)));
            actions2.getChildren().add(refireBtn);
        }
        if (sessionContext.hasPermission(PermissionCode.KITCHEN_TICKET_CANCEL)) {
            SecondaryButton cancelBtn = new SecondaryButton("Cancel");
            cancelBtn.setOnAction(e -> promptReasonAndRun("Cancel Ticket", "Cancellation reason:", r ->
                    runTicketAction(t.id(), "cancel", t.version(), r)));
            actions2.getChildren().add(cancelBtn);
        }

        card.getChildren().addAll(head, metaLbl, timerLbl, itemsBox, actions1, actions2);
        return card;
    }

    private void runTicketAction(UUID ticketId, String action, long version, String reason) {
        String token = sessionContext.accessToken();
        KitchenTicketActionRequest req = new KitchenTicketActionRequest(version, reason, null);
        Task<KitchenTicketDto> task = new Task<>() {
            @Override
            protected KitchenTicketDto call() throws Exception {
                return switch (action) {
                    case "accept" -> serverClient.acceptKitchenTicket(token, ticketId, req).data();
                    case "start" -> serverClient.startKitchenTicket(token, ticketId, req).data();
                    case "ready" -> serverClient.markKitchenTicketReady(token, ticketId, req).data();
                    case "serve" -> serverClient.serveKitchenTicket(token, ticketId, req).data();
                    case "hold" -> serverClient.holdKitchenTicket(token, ticketId, req).data();
                    case "release" -> serverClient.releaseKitchenTicket(token, ticketId, req).data();
                    case "cancel" -> serverClient.cancelKitchenTicket(token, ticketId, req).data();
                    case "refire" -> serverClient.refireKitchenTicket(token, ticketId, req).data();
                    default -> throw new IllegalArgumentException(action);
                };
            }
        };
        task.setOnSucceeded(e -> refreshTickets());
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void runItemAction(UUID ticketId, UUID itemId, String status, long ticketVersion) {
        String token = sessionContext.accessToken();
        Task<KitchenTicketDto> task = new Task<>() {
            @Override
            protected KitchenTicketDto call() throws Exception {
                return serverClient.updateKitchenItemStatus(
                        token, ticketId, itemId, new UpdateKitchenItemStatusRequest(status, ticketVersion, null)
                ).data();
            }
        };
        task.setOnSucceeded(e -> refreshTickets());
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void resolveUnrouted(UUID unroutedId, UUID stationId) {
        String token = sessionContext.accessToken();
        Task<KitchenTicketDto> task = new Task<>() {
            @Override
            protected KitchenTicketDto call() throws Exception {
                return serverClient.resolveUnroutedItem(token, unroutedId, new ResolveUnroutedItemRequest(stationId, true)).data();
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Unrouted item dispatched and rule saved.");
            refreshTickets();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void retryPrint(UUID jobId) {
        String token = sessionContext.accessToken();
        Task<KitchenPrintJobDto> task = new Task<>() {
            @Override
            protected KitchenPrintJobDto call() throws Exception {
                return serverClient.retryKitchenPrintJob(token, jobId).data();
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Print retry status: " + task.getValue().status());
            refreshTickets();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void promptReasonAndRun(String title, String header, java.util.function.Consumer<String> action) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle(title);
        dlg.setHeaderText(header);
        dlg.showAndWait().ifPresent(raw -> {
            if (!raw.isBlank()) {
                action.accept(raw.trim());
            }
        });
    }

    private void handleApiError(Throwable ex) {
        if (ex instanceof ApiClientException ace) {
            if (ace.statusCode() == 401) {
                onSessionExpired.run();
                return;
            }
            if (ace.statusCode() == 409) {
                banner.showError("Ticket updated on another display — refreshing queue.");
                refreshTickets();
                return;
            }
        }
        banner.showError(ex != null ? ex.getMessage() : "KDS request failed.");
    }

    private static String formatElapsed(long seconds) {
        long min = seconds / 60;
        long sec = seconds % 60;
        return String.format("%02d:%02d", min, sec);
    }

    private record KdsLoadResult(
            List<KitchenTicketDto> tickets,
            List<KitchenUnroutedItemDto> unrouted,
            List<KitchenPrintJobDto> failedPrints
    ) {
    }
}
