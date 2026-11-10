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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Kitchen Display System (KDS) view showing high-contrast bump cards, status-colored headers,
 * SLA timers, modifier/note callouts, single primary next-step actions, and subtle secondary controls.
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
    private final StatusBadge summaryBadge = new StatusBadge("0 Active", StatusBadge.Tone.NEUTRAL);
    private final HBox alertBar = new HBox(8);
    private final FlowPane ticketsFlow = new FlowPane(16, 16);

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

        Label title = new Label("Kitchen Display (KDS)");
        title.getStyleClass().add("pos-heading-lg");

        SecondaryButton refreshBtn = new SecondaryButton("Refresh [F5]");
        refreshBtn.setOnAction(e -> refreshAll());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(10, title, new Label("Station:"), stationCombo, new Label("Sort:"), sortCombo,
                refreshBtn, spacer, summaryBadge, syncBadge);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("pos-card");
        topBar.setPadding(new Insets(12, 16, 12, 16));

        alertBar.setAlignment(Pos.CENTER_LEFT);
        alertBar.setVisible(false);
        alertBar.setManaged(false);

        ticketsFlow.setPadding(new Insets(2, 4, 12, 2));
        ScrollPane scroll = new ScrollPane(ticketsFlow);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("pos-scroll-pane");
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
                ticketsFlow.getChildren().setAll(buildEmptyCard(
                        "No Active Kitchen Stations",
                        "Configure an active kitchen station for this branch to view incoming tickets."
                ));
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
            long prepCount = res.tickets().stream()
                    .filter(t -> "PREPARING".equals(t.status()) || "ACCEPTED".equals(t.status())).count();
            long readyCount = res.tickets().stream()
                    .filter(t -> "READY".equals(t.status())).count();
            summaryBadge.setText(res.tickets().size() + " Active • " + prepCount + " Prep • " + readyCount + " Ready");
            syncBadge.setText("Live");
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
            ticketsFlow.getChildren().add(buildEmptyCard(
                    "All Caught Up — " + selectedStation.name(),
                    "No open tickets in queue. New orders sent from the POS terminal will appear here automatically."
            ));
            return;
        }
        for (KitchenTicketDto ticket : tickets) {
            ticketsFlow.getChildren().add(buildTicketCard(ticket));
        }
    }

    private VBox buildTicketCard(KitchenTicketDto t) {
        VBox card = new VBox(0);
        card.getStyleClass().add("pos-kds-card");
        card.setMinWidth(340);
        card.setPrefWidth(355);
        card.setMaxWidth(365);

        String headerClass = resolveHeaderClass(t);
        String shortOrder = shortenCode(t.orderNumberSnapshot());
        String headline = t.tableLabelSnapshot() != null && !t.tableLabelSnapshot().isBlank()
                ? "Table " + t.tableLabelSnapshot() + "  •  #" + shortOrder
                : t.orderTypeSnapshot() + "  •  #" + shortOrder;

        Label titleLbl = new Label(headline);
        titleLbl.getStyleClass().add("pos-kds-title");

        String sub = t.status()
                + (!"INITIAL".equals(t.ticketKind()) ? " • " + t.ticketKind() : "")
                + (!"NORMAL".equals(t.priorityLevel()) ? " • " + t.priorityLevel() : "");
        Label subLbl = new Label(sub);
        subLbl.getStyleClass().add("pos-kds-subtitle");
        VBox headLeft = new VBox(2, titleLbl, subLbl);
        HBox.setHgrow(headLeft, Priority.ALWAYS);

        String elapsedText = formatElapsed(t.elapsedSeconds()) + " / " + t.expectedPrepMinutes() + "m"
                + (t.overdue() ? " LATE" : "");
        Label timerLbl = new Label(elapsedText);
        timerLbl.getStyleClass().add("pos-kds-timer");
        timerLbl.setMinWidth(Region.USE_PREF_SIZE);

        HBox header = new HBox(10, headLeft, timerLbl);
        header.getStyleClass().addAll("pos-kds-header", headerClass);
        header.setAlignment(Pos.CENTER_LEFT);
        Tooltip.install(header, new Tooltip("Ticket: " + t.ticketNumber() + "\nOrder: " + t.orderNumberSnapshot()));

        VBox itemsBox = new VBox(8);
        boolean canReadyItem = sessionContext.hasAnyPermission(PermissionCode.KITCHEN_TICKET_READY, PermissionCode.KITCHEN_UPDATE);
        for (KitchenTicketItemDto item : t.items()) {
            itemsBox.getChildren().add(buildTicketItemRow(t, item, canReadyItem));
        }

        Region bodySpacer = new Region();
        VBox.setVgrow(bodySpacer, Priority.ALWAYS);

        VBox controlsBox = buildControlsSection(t);

        VBox body = new VBox(12, itemsBox, bodySpacer, controlsBox);
        body.getStyleClass().add("pos-kds-body");
        VBox.setVgrow(body, Priority.ALWAYS);

        card.getChildren().addAll(header, body);
        return card;
    }

    private VBox buildTicketItemRow(KitchenTicketDto t, KitchenTicketItemDto item, boolean canReadyItem) {
        String qty = item.quantity().stripTrailingZeros().toPlainString();
        Label qtyBadge = new Label(qty + "x");
        qtyBadge.getStyleClass().add("pos-kds-qty");
        qtyBadge.setMinWidth(Region.USE_PREF_SIZE);

        Label nameLbl = new Label(item.itemNameSnapshot());
        nameLbl.getStyleClass().add("pos-kds-item-name");
        nameLbl.setWrapText(true);
        HBox.setHgrow(nameLbl, Priority.ALWAYS);

        HBox topRow = new HBox(8, qtyBadge, nameLbl);
        topRow.setAlignment(Pos.CENTER_LEFT);

        boolean itemDone = "READY".equals(item.status()) || "SERVED".equals(item.status());
        if (itemDone) {
            StatusBadge donePill = new StatusBadge(item.status(), StatusBadge.Tone.SUCCESS);
            donePill.setMinWidth(Region.USE_PREF_SIZE);
            topRow.getChildren().add(donePill);
        } else if (canReadyItem && !"CANCELLED".equals(item.status()) && !"ON_HOLD".equals(t.status())) {
            Button itemDoneBtn = new Button("Done");
            itemDoneBtn.getStyleClass().add("pos-kds-subtle-btn");
            itemDoneBtn.setMinWidth(Region.USE_PREF_SIZE);
            itemDoneBtn.setOnAction(e -> runItemAction(t.id(), item.id(), "READY", t.version()));
            topRow.getChildren().add(itemDoneBtn);
        }

        VBox rowBox = new VBox(5, topRow);
        rowBox.getStyleClass().add("pos-kds-item-row");
        if (itemDone) {
            rowBox.getStyleClass().add("pos-kds-item-ready");
        }

        if (!item.selectedModifiers().isEmpty()) {
            Label modsLbl = new Label("+ " + String.join(", ", item.selectedModifiers()));
            modsLbl.getStyleClass().add("pos-text-muted");
            modsLbl.setPadding(new Insets(0, 0, 0, 34));
            modsLbl.setWrapText(true);
            rowBox.getChildren().add(modsLbl);
        }
        if (item.notes() != null && !item.notes().isBlank()) {
            Label noteLbl = new Label("NOTE: " + item.notes());
            noteLbl.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #92400E; -fx-padding: 3 7; -fx-background-radius: 4; -fx-font-size: 12px; -fx-font-weight: 600;");
            noteLbl.setWrapText(true);
            rowBox.getChildren().add(noteLbl);
        }
        return rowBox;
    }

    private VBox buildControlsSection(KitchenTicketDto t) {
        VBox box = new VBox(8);

        Button primaryAction = buildPrimaryActionButton(t);
        if (primaryAction != null) {
            primaryAction.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().add(primaryAction);
        }

        HBox secondaryRow = new HBox(6);
        secondaryRow.setAlignment(Pos.CENTER_LEFT);

        if (("NEW".equals(t.status()) || "QUEUED".equals(t.status()))
                && sessionContext.hasAnyPermission(PermissionCode.KITCHEN_TICKET_ACCEPT, PermissionCode.KITCHEN_UPDATE)) {
            Button accBtn = subtleButton("Accept");
            accBtn.setOnAction(e -> runTicketAction(t.id(), "accept", t.version(), null));
            secondaryRow.getChildren().add(accBtn);
        }
        if (sessionContext.hasPermission(PermissionCode.KITCHEN_TICKET_HOLD)) {
            if ("ON_HOLD".equals(t.status())) {
                Button relBtn = subtleButton("Resume");
                relBtn.setOnAction(e -> runTicketAction(t.id(), "release", t.version(), "Released"));
                secondaryRow.getChildren().add(relBtn);
            } else if (!"READY".equals(t.status())) {
                Button holdBtn = subtleButton("Hold");
                holdBtn.setOnAction(e -> promptReasonAndRun("Hold Ticket", "Station hold reason:", r ->
                        runTicketAction(t.id(), "hold", t.version(), r)));
                secondaryRow.getChildren().add(holdBtn);
            }
        }
        if (sessionContext.hasPermission(PermissionCode.KITCHEN_TICKET_REFIRE)) {
            Button refireBtn = subtleButton("Re-fire");
            refireBtn.setOnAction(e -> promptReasonAndRun("Re-fire Ticket", "Re-fire reason:", r ->
                    runTicketAction(t.id(), "refire", t.version(), r)));
            secondaryRow.getChildren().add(refireBtn);
        }
        if (sessionContext.hasPermission(PermissionCode.KITCHEN_TICKET_CANCEL)) {
            Button cancelBtn = subtleButton("Cancel");
            cancelBtn.setOnAction(e -> promptReasonAndRun("Cancel Ticket", "Cancellation reason:", r ->
                    runTicketAction(t.id(), "cancel", t.version(), r)));
            secondaryRow.getChildren().add(cancelBtn);
        }

        if (!secondaryRow.getChildren().isEmpty()) {
            for (var child : secondaryRow.getChildren()) {
                if (child instanceof Button b) {
                    b.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(b, Priority.ALWAYS);
                }
            }
            box.getChildren().add(secondaryRow);
        }
        return box;
    }

    private Button buildPrimaryActionButton(KitchenTicketDto t) {
        switch (t.status()) {
            case "NEW", "QUEUED", "ACCEPTED" -> {
                if (sessionContext.hasAnyPermission(PermissionCode.KITCHEN_TICKET_START, PermissionCode.KITCHEN_UPDATE)) {
                    PrimaryButton startBtn = new PrimaryButton("Start Prep");
                    startBtn.setOnAction(e -> runTicketAction(t.id(), "start", t.version(), null));
                    return startBtn;
                }
            }
            case "PREPARING", "IN_PROGRESS" -> {
                if (sessionContext.hasAnyPermission(PermissionCode.KITCHEN_TICKET_READY, PermissionCode.KITCHEN_UPDATE)) {
                    Button readyBtn = new Button("Mark Ready");
                    readyBtn.getStyleClass().add("pos-btn-checkout");
                    readyBtn.setOnAction(e -> runTicketAction(t.id(), "ready", t.version(), null));
                    return readyBtn;
                }
            }
            case "READY" -> {
                if (sessionContext.hasAnyPermission(PermissionCode.KITCHEN_TICKET_SERVE, PermissionCode.KITCHEN_UPDATE)) {
                    Button serveBtn = new Button("Bump / Mark Served");
                    serveBtn.getStyleClass().add("pos-btn-checkout");
                    serveBtn.setOnAction(e -> runTicketAction(t.id(), "serve", t.version(), null));
                    return serveBtn;
                }
            }
            case "ON_HOLD" -> {
                if (sessionContext.hasPermission(PermissionCode.KITCHEN_TICKET_HOLD)) {
                    PrimaryButton relBtn = new PrimaryButton("Resume Prep");
                    relBtn.setOnAction(e -> runTicketAction(t.id(), "release", t.version(), "Released"));
                    return relBtn;
                }
            }
            default -> {
            }
        }
        if (!"READY".equals(t.status()) && !"ON_HOLD".equals(t.status())
                && sessionContext.hasAnyPermission(PermissionCode.KITCHEN_TICKET_READY, PermissionCode.KITCHEN_UPDATE)) {
            Button readyBtn = new Button("Mark Ready");
            readyBtn.getStyleClass().add("pos-btn-checkout");
            readyBtn.setOnAction(e -> runTicketAction(t.id(), "ready", t.version(), null));
            return readyBtn;
        }
        return null;
    }

    private static Button subtleButton(String label) {
        Button btn = new Button(label);
        btn.getStyleClass().add("pos-kds-subtle-btn");
        return btn;
    }

    private static String resolveHeaderClass(KitchenTicketDto t) {
        if (t.overdue()) {
            return "pos-kds-header-overdue";
        }
        return switch (t.status()) {
            case "READY" -> "pos-kds-header-ready";
            case "ON_HOLD" -> "pos-kds-header-hold";
            case "ACCEPTED", "PREPARING", "IN_PROGRESS" -> "pos-kds-header-prep";
            default -> "pos-kds-header-new";
        };
    }

    private static String shortenCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "TICKET";
        }
        int lastDash = raw.lastIndexOf('-');
        if (lastDash >= 0 && lastDash < raw.length() - 1) {
            return raw.substring(lastDash + 1);
        }
        return raw.length() > 8 ? raw.substring(raw.length() - 8) : raw;
    }

    private static VBox buildEmptyCard(String titleText, String subtitleText) {
        Label titleLbl = new Label(titleText);
        titleLbl.getStyleClass().add("pos-heading-md");
        Label subLbl = new Label(subtitleText);
        subLbl.getStyleClass().add("pos-text-muted");
        subLbl.setWrapText(true);
        VBox box = new VBox(6, titleLbl, subLbl);
        box.getStyleClass().add("pos-state-box");
        box.setPrefWidth(480);
        return box;
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
