package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.StatusBadge;
import com.restaurantpos.shared.dto.payment.CashMovementDto;
import com.restaurantpos.shared.dto.payment.CashSessionDto;
import com.restaurantpos.shared.dto.payment.CloseCashSessionRequest;
import com.restaurantpos.shared.dto.payment.DrawerEventDto;
import com.restaurantpos.shared.dto.payment.OpenCashSessionRequest;
import com.restaurantpos.shared.dto.payment.OpenDrawerRequest;
import com.restaurantpos.shared.dto.payment.PaymentAttemptDto;
import com.restaurantpos.shared.dto.payment.ReconciliationReportDto;
import com.restaurantpos.shared.dto.payment.RecordCashMovementRequest;
import com.restaurantpos.shared.dto.payment.ReopenCashSessionRequest;
import com.restaurantpos.shared.security.PermissionCode;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Cashier cash shift management, append-only cash movements, counted close/variance display,
 * manual drawer trigger, and uncertain transaction reconciliation dashboard.
 */
public final class CashManagementView extends VBox {

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final StatusBadge activeBadge = new StatusBadge("No Active Session", StatusBadge.Tone.NEUTRAL);
    private final Label expectedBalanceLabel = new Label("Server Expected Cash: --");

    private final TextField openingFloatField = new TextField("100.00");
    private final PrimaryButton openSessionBtn = new PrimaryButton("Open Shift");

    private final ComboBox<String> movementTypeCombo = new ComboBox<>();
    private final TextField movementAmountField = new TextField("25.00");
    private final TextField movementReasonField = new TextField();
    private final SecondaryButton recordMovementBtn = new SecondaryButton("Record Movement");

    private final TextField countedAmountField = new TextField();
    private final Label liveVarianceLabel = new Label("Variance: --");
    private final PrimaryButton closeSessionBtn = new PrimaryButton("Close Cash Session");

    private final TextField drawerReasonField = new TextField("Change check");
    private final SecondaryButton openDrawerBtn = new SecondaryButton("Open Cash Drawer (No Sale)");

    private final VBox movementsBox = new VBox(6);
    private final VBox historyBox = new VBox(6);
    private final VBox reconciliationBox = new VBox(6);

    private CashSessionDto activeSession;

    public CashManagementView(
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

        movementTypeCombo.getItems().addAll("PAY_IN", "PAY_OUT", "WITHDRAWAL", "DEPOSIT", "SAFE_DROP");
        movementTypeCombo.setValue("PAY_IN");
        movementReasonField.setPromptText("Movement reason");
        countedAmountField.setPromptText("Counted cash on close");
        countedAmountField.textProperty().addListener((o, ov, nv) -> updateVarianceLabel());

        Label heading = new Label("Cash Sessions & Financial Reconciliation");
        heading.getStyleClass().add("pos-heading-lg");
        SecondaryButton refreshBtn = new SecondaryButton("Refresh");
        refreshBtn.setOnAction(e -> loadData());
        HBox topRow = new HBox(12, heading, activeBadge, refreshBtn);
        topRow.setAlignment(Pos.CENTER_LEFT);

        openSessionBtn.setOnAction(e -> openSession());
        recordMovementBtn.setOnAction(e -> recordMovement());
        closeSessionBtn.setOnAction(e -> closeSession());
        openDrawerBtn.setOnAction(e -> openDrawer());

        expectedBalanceLabel.getStyleClass().add("pos-total-label");

        HBox openRow = new HBox(8, new Label("Opening Float:"), openingFloatField, openSessionBtn);
        openRow.setAlignment(Pos.CENTER_LEFT);

        HBox moveRow = new HBox(8, movementTypeCombo, movementAmountField, movementReasonField, recordMovementBtn);
        moveRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(movementReasonField, Priority.ALWAYS);

        HBox closeRow = new HBox(8, new Label("Counted Cash:"), countedAmountField, liveVarianceLabel, closeSessionBtn);
        closeRow.setAlignment(Pos.CENTER_LEFT);

        HBox drawerRow = new HBox(8, new Label("Drawer Reason:"), drawerReasonField, openDrawerBtn);
        drawerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(drawerReasonField, Priority.ALWAYS);

        VBox controlsCard = new VBox(10, expectedBalanceLabel, openRow, moveRow, closeRow, drawerRow);
        controlsCard.getStyleClass().add("pos-card");

        ScrollPane movesScroll = new ScrollPane(movementsBox);
        movesScroll.setFitToWidth(true);
        VBox movesCard = new VBox(8, new Label("Active Session Append-Only Movements"), movesScroll);
        movesCard.getStyleClass().add("pos-card");
        VBox.setVgrow(movesScroll, Priority.ALWAYS);
        HBox.setHgrow(movesCard, Priority.ALWAYS);

        ScrollPane histScroll = new ScrollPane(historyBox);
        histScroll.setFitToWidth(true);
        VBox histCard = new VBox(8, new Label("Branch Cash Sessions"), histScroll);
        histCard.getStyleClass().add("pos-card");
        VBox.setVgrow(histScroll, Priority.ALWAYS);
        HBox.setHgrow(histCard, Priority.ALWAYS);

        ScrollPane recScroll = new ScrollPane(reconciliationBox);
        recScroll.setFitToWidth(true);
        VBox recCard = new VBox(8, new Label("Uncertain Card Payments / Reconciliation"), recScroll);
        recCard.getStyleClass().add("pos-card");
        VBox.setVgrow(recScroll, Priority.ALWAYS);
        HBox.setHgrow(recCard, Priority.ALWAYS);

        HBox columns = new HBox(12, movesCard, histCard, recCard);
        VBox.setVgrow(columns, Priority.ALWAYS);

        getChildren().addAll(topRow, banner, controlsCard, columns);
        loadData();
    }

    public void loadData() {
        String token = sessionContext.accessToken();
        UUID terminalId = sessionContext.profile() != null ? sessionContext.profile().terminalId() : null;
        Task<LoadedCashData> task = new Task<>() {
            @Override
            protected LoadedCashData call() throws Exception {
                CashSessionDto active = null;
                try {
                    active = serverClient.getActiveCashSession(token, terminalId).data();
                } catch (ApiClientException ignored) {
                }
                List<CashSessionDto> history = serverClient.listCashSessions(token, 0, 25).data();
                ReconciliationReportDto recon = null;
                if (sessionContext.hasPermission(PermissionCode.REPORTS_PAYMENTS_VIEW)) {
                    recon = serverClient.getReconciliationOverview(token).data();
                }
                return new LoadedCashData(active, history, recon);
            }
        };
        task.setOnSucceeded(e -> render(task.getValue()));
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void render(LoadedCashData data) {
        this.activeSession = data.active();
        boolean hasActive = activeSession != null;

        if (hasActive) {
            activeBadge.setText(activeSession.status() + " (" + activeSession.currencyCode() + ")");
            activeBadge.setTone(StatusBadge.Tone.SUCCESS);
            expectedBalanceLabel.setText("Server Expected Cash: " + activeSession.currencyCode()
                    + " " + fmt(activeSession.derivedExpectedCash()));
        } else {
            activeBadge.setText("No Open Session");
            activeBadge.setTone(StatusBadge.Tone.WARNING);
            expectedBalanceLabel.setText("Server Expected Cash: No open cash session on terminal");
        }

        openSessionBtn.setDisable(hasActive || !sessionContext.hasPermission(PermissionCode.CASH_SESSION_OPEN));
        recordMovementBtn.setDisable(!hasActive || !sessionContext.hasPermission(PermissionCode.CASH_MOVEMENT_CREATE));
        closeSessionBtn.setDisable(!hasActive || !sessionContext.hasPermission(PermissionCode.CASH_SESSION_CLOSE));
        openDrawerBtn.setDisable(!sessionContext.hasPermission(PermissionCode.CASH_DRAWER_OPEN_WITHOUT_SALE));

        movementsBox.getChildren().clear();
        if (hasActive) {
            for (CashMovementDto m : activeSession.movements()) {
                Label lbl = new Label(m.movementType() + "  " + activeSession.currencyCode()
                        + " " + fmt(m.amount()) + " — " + m.reason());
                lbl.setWrapText(true);
                movementsBox.getChildren().add(lbl);
            }
        }

        historyBox.getChildren().clear();
        for (CashSessionDto s : data.history()) {
            String summary = s.status() + " | Open " + fmt(s.openingFloat())
                    + " | Exp " + fmt(s.derivedExpectedCash())
                    + (s.countedCashAmount() != null ? " | Cnt " + fmt(s.countedCashAmount()) : "")
                    + (s.varianceAmount() != null ? " | Var " + fmt(s.varianceAmount()) : "");
            Label lbl = new Label(summary);
            lbl.setWrapText(true);
            HBox row = new HBox(6, lbl);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(lbl, Priority.ALWAYS);

            if ("CLOSED".equals(s.status()) && sessionContext.hasPermission(PermissionCode.CASH_SESSION_RECONCILE)) {
                SecondaryButton recBtn = new SecondaryButton("Reconcile");
                recBtn.setOnAction(e -> reconcileCashSession(s));
                row.getChildren().add(recBtn);
            }
            if ("CLOSED".equals(s.status()) && sessionContext.hasPermission(PermissionCode.CASH_SESSION_REOPEN)) {
                SecondaryButton reopenBtn = new SecondaryButton("Reopen");
                reopenBtn.setOnAction(e -> reopenCashSession(s));
                row.getChildren().add(reopenBtn);
            }
            historyBox.getChildren().add(row);
        }

        reconciliationBox.getChildren().clear();
        if (data.reconciliation() != null) {
            for (PaymentAttemptDto a : data.reconciliation().pendingOrUncertainAttempts()) {
                Label lbl = new Label("UNCERTAIN: " + a.currencyCode() + " " + fmt(a.requestedAmount())
                        + " (" + a.providerName() + ")");
                SecondaryButton queryBtn = new SecondaryButton("Query & Reconcile");
                queryBtn.setDisable(!sessionContext.hasPermission(PermissionCode.CASH_SESSION_RECONCILE));
                queryBtn.setOnAction(e -> queryAndReconcileAttempt(a.id()));
                HBox row = new HBox(6, lbl, queryBtn);
                row.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(lbl, Priority.ALWAYS);
                reconciliationBox.getChildren().add(row);
            }
            for (var r : data.reconciliation().failedRefunds()) {
                Label lbl = new Label("FAILED REFUND: " + r.currencyCode() + " " + fmt(r.amount()) + " — " + r.failureReason());
                lbl.getStyleClass().add("pos-text-danger");
                reconciliationBox.getChildren().add(lbl);
            }
            if (reconciliationBox.getChildren().isEmpty()) {
                reconciliationBox.getChildren().add(new Label("No unresolved uncertain card attempts or failed refunds."));
            }
        }
        updateVarianceLabel();
    }

    private void updateVarianceLabel() {
        if (activeSession == null || countedAmountField.getText() == null || countedAmountField.getText().isBlank()) {
            liveVarianceLabel.setText("Variance: --");
            return;
        }
        try {
            BigDecimal counted = new BigDecimal(countedAmountField.getText().trim()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal var = counted.subtract(activeSession.derivedExpectedCash());
            liveVarianceLabel.setText("Variance: " + activeSession.currencyCode() + " " + fmt(var));
        } catch (NumberFormatException ignored) {
            liveVarianceLabel.setText("Variance: --");
        }
    }

    private void openSession() {
        String token = sessionContext.accessToken();
        UUID termId = sessionContext.profile() != null ? sessionContext.profile().terminalId() : null;
        BigDecimal opening;
        try {
            opening = new BigDecimal(openingFloatField.getText().trim());
        } catch (NumberFormatException ex) {
            banner.showError("Enter a valid opening float.");
            return;
        }
        Task<CashSessionDto> task = new Task<>() {
            @Override
            protected CashSessionDto call() throws Exception {
                return serverClient.openCashSession(token, new OpenCashSessionRequest(termId, opening)).data();
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Cash session opened.");
            loadData();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void recordMovement() {
        if (activeSession == null) return;
        String token = sessionContext.accessToken();
        BigDecimal amt;
        try {
            amt = new BigDecimal(movementAmountField.getText().trim());
        } catch (NumberFormatException ex) {
            banner.showError("Enter a valid movement amount.");
            return;
        }
        String reason = movementReasonField.getText() != null && !movementReasonField.getText().isBlank()
                ? movementReasonField.getText().trim() : "Cash adjustment";
        RecordCashMovementRequest req = new RecordCashMovementRequest(
                movementTypeCombo.getValue(), amt, reason, UUID.randomUUID().toString());
        Task<CashSessionDto> task = new Task<>() {
            @Override
            protected CashSessionDto call() throws Exception {
                return serverClient.recordCashMovement(token, activeSession.id(), req).data();
            }
        };
        task.setOnSucceeded(e -> {
            movementReasonField.clear();
            banner.showSuccess("Cash movement recorded.");
            loadData();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void closeSession() {
        if (activeSession == null) return;
        String token = sessionContext.accessToken();
        BigDecimal counted;
        try {
            counted = new BigDecimal(countedAmountField.getText().trim());
        } catch (Exception ex) {
            banner.showError("Enter counted cash amount to close session.");
            return;
        }
        Task<CashSessionDto> task = new Task<>() {
            @Override
            protected CashSessionDto call() throws Exception {
                return serverClient.closeCashSession(
                        token, activeSession.id(), new CloseCashSessionRequest(counted, activeSession.version())).data();
            }
        };
        task.setOnSucceeded(e -> {
            CashSessionDto closed = task.getValue();
            banner.showSuccess("Session closed. Variance: " + closed.currencyCode() + " " + fmt(closed.varianceAmount()));
            countedAmountField.clear();
            loadData();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void openDrawer() {
        String token = sessionContext.accessToken();
        UUID termId = sessionContext.profile() != null ? sessionContext.profile().terminalId() : null;
        String reason = drawerReasonField.getText() != null ? drawerReasonField.getText().trim() : "";
        Task<DrawerEventDto> task = new Task<>() {
            @Override
            protected DrawerEventDto call() throws Exception {
                return serverClient.openDrawerManually(
                        token, new OpenDrawerRequest(activeSession != null ? activeSession.id() : null, termId, reason, null)).data();
            }
        };
        task.setOnSucceeded(e -> {
            DrawerEventDto ev = task.getValue();
            if (ev.openedOk()) {
                banner.showSuccess("Cash drawer opened (" + ev.hardwareDeviceName() + ").");
            } else {
                banner.showError("Drawer unavailable: " + ev.errorMessage());
            }
            loadData();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void reconcileCashSession(CashSessionDto s) {
        String token = sessionContext.accessToken();
        Task<CashSessionDto> task = new Task<>() {
            @Override
            protected CashSessionDto call() throws Exception {
                return serverClient.reconcileCashSession(token, s.id(), s.version()).data();
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Cash session marked RECONCILED.");
            loadData();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void reopenCashSession(CashSessionDto s) {
        String token = sessionContext.accessToken();
        Task<CashSessionDto> task = new Task<>() {
            @Override
            protected CashSessionDto call() throws Exception {
                return serverClient.reopenCashSession(
                        token, s.id(), new ReopenCashSessionRequest("Manager authorized reopen", s.version())).data();
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Cash session reopened with audit record.");
            loadData();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void queryAndReconcileAttempt(UUID attemptId) {
        String token = sessionContext.accessToken();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                serverClient.reconcileAttempt(token, attemptId);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Card terminal status queried and finalized.");
            loadData();
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void handleApiError(Throwable ex) {
        if (ex instanceof ApiClientException ace && ace.statusCode() == 401) {
            onSessionExpired.run();
            return;
        }
        banner.showError(ex != null ? ex.getMessage() : "Request failed.");
    }

    private static String fmt(BigDecimal val) {
        return (val != null ? val : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record LoadedCashData(
            CashSessionDto active,
            List<CashSessionDto> history,
            ReconciliationReportDto reconciliation
    ) {
    }
}
