package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.StatusBadge;
import com.restaurantpos.shared.dto.payment.CashSessionDto;
import com.restaurantpos.shared.dto.payment.GenerateReceiptRequest;
import com.restaurantpos.shared.dto.payment.OrderPaymentSummaryDto;
import com.restaurantpos.shared.dto.payment.PaymentAttemptDto;
import com.restaurantpos.shared.dto.payment.PaymentDto;
import com.restaurantpos.shared.dto.payment.PaymentMethodDto;
import com.restaurantpos.shared.dto.payment.ProcessPaymentRequest;
import com.restaurantpos.shared.dto.payment.ReceiptDto;
import com.restaurantpos.shared.dto.payment.RefundRequest;
import com.restaurantpos.shared.dto.pos.OrderDto;
import com.restaurantpos.shared.dto.security.SupervisorApprovalRequest;
import com.restaurantpos.shared.security.PermissionCode;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Interactive Checkout, Split/Partial Tender, Card Terminal State, Refund,
 * and Receipt Preview/Reprint modal synchronized strictly with Local Server authority.
 */
public final class CheckoutDialog {

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final UUID orderId;
    private final Runnable onPaymentChanged;
    private final Runnable onSessionExpired;

    private final Stage stage = new Stage();
    private final NotificationBanner banner = new NotificationBanner();
    private final Label orderTitleLabel = new Label("Checkout");
    private final StatusBadge orderStatusBadge = new StatusBadge("OPEN", StatusBadge.Tone.NEUTRAL);
    private final StatusBadge sessionBadge = new StatusBadge("Cash Session: Checking", StatusBadge.Tone.NEUTRAL);
    private final Label totalLabel = new Label("Total: 0.00");
    private final Label paidLabel = new Label("Paid: 0.00");
    private final Label remainingLabel = new Label("Remaining Due: 0.00");
    private final Label changeLabel = new Label("Cash Change: 0.00");

    private final ComboBox<PaymentMethodDto> methodCombo = new ComboBox<>();
    private final TextField amountField = new TextField();
    private final TextField tenderedField = new TextField();
    private final PrimaryButton payBtn = new PrimaryButton("Process Payment");
    private final SecondaryButton reconcileAttemptBtn = new SecondaryButton("Query Uncertain Card Status");

    private final VBox paymentsListBox = new VBox(6);
    private final VBox receiptsListBox = new VBox(6);
    private final TextArea receiptPreviewArea = new TextArea();
    private final ComboBox<String> languageCombo = new ComboBox<>();
    private final SecondaryButton generateReceiptBtn = new SecondaryButton("Generate & Print Receipt");

    private OrderPaymentSummaryDto currentSummary;
    private CashSessionDto activeCashSession;
    private List<ReceiptDto> currentReceipts = List.of();
    private String activeIdempotencyKey = UUID.randomUUID().toString();

    public CheckoutDialog(
            LocalServerClient serverClient,
            SessionContext sessionContext,
            ExecutorService ioExecutor,
            OrderDto order,
            Runnable onPaymentChanged,
            Runnable onSessionExpired
    ) {
        this.serverClient = serverClient;
        this.sessionContext = sessionContext;
        this.ioExecutor = ioExecutor;
        this.orderId = order.id();
        this.onPaymentChanged = onPaymentChanged;
        this.onSessionExpired = onSessionExpired;

        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Checkout — " + order.orderNumber());
        stage.setMinWidth(920);
        stage.setMinHeight(660);

        receiptPreviewArea.setEditable(false);
        receiptPreviewArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");
        receiptPreviewArea.setPrefRowCount(18);
        languageCombo.getItems().addAll("BILINGUAL", "EN", "AR");
        languageCombo.setValue("BILINGUAL");

        methodCombo.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(PaymentMethodDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.name() + " (" + item.methodType() + ")");
            }
        });
        methodCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(PaymentMethodDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.name() + " (" + item.methodType() + ")");
            }
        });

        amountField.textProperty().addListener((obs, o, n) -> updateChangeReadout());
        tenderedField.textProperty().addListener((obs, o, n) -> updateChangeReadout());
        methodCombo.valueProperty().addListener((obs, o, n) -> updateMethodUiState());

        VBox root = buildLayout();
        Scene scene = new Scene(root, 960, 700);
        var cssUrl = getClass().getResource("/com/restaurantpos/desktop/css/pos-theme.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        stage.setScene(scene);

        loadAllState();
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    private VBox buildLayout() {
        orderTitleLabel.getStyleClass().add("pos-heading-lg");
        remainingLabel.getStyleClass().add("pos-total-label");
        changeLabel.getStyleClass().add("pos-heading-md");

        HBox header = new HBox(12, orderTitleLabel, orderStatusBadge, sessionBadge);
        header.setAlignment(Pos.CENTER_LEFT);

        // Left: Tender & Payments History
        VBox balancesCard = new VBox(4, totalLabel, paidLabel, remainingLabel);
        balancesCard.getStyleClass().add("pos-module-card");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(new Label("Payment Method:"), 0, 0);
        form.add(methodCombo, 1, 0);
        form.add(new Label("Amount to Apply:"), 0, 1);
        form.add(amountField, 1, 1);
        form.add(new Label("Cash Tendered:"), 0, 2);
        form.add(tenderedField, 1, 2);

        SecondaryButton exactBtn = new SecondaryButton("Exact");
        exactBtn.setOnAction(e -> {
            if (currentSummary != null) {
                String rem = fmt(currentSummary.remainingDue());
                amountField.setText(rem);
                tenderedField.setText(rem);
            }
        });
        SecondaryButton halfBtn = new SecondaryButton("Half Split");
        halfBtn.setOnAction(e -> {
            if (currentSummary != null) {
                BigDecimal half = currentSummary.remainingDue().divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
                String val = fmt(half);
                amountField.setText(val);
                tenderedField.setText(val);
            }
        });
        SecondaryButton add10Btn = new SecondaryButton("+10 Cash");
        add10Btn.setOnAction(e -> bumpTendered(new BigDecimal("10")));
        SecondaryButton add50Btn = new SecondaryButton("+50 Cash");
        add50Btn.setOnAction(e -> bumpTendered(new BigDecimal("50")));
        HBox quickRow = new HBox(8, exactBtn, halfBtn, add10Btn, add50Btn);

        payBtn.setMaxWidth(Double.MAX_VALUE);
        payBtn.setOnAction(e -> submitPayment());

        reconcileAttemptBtn.setMaxWidth(Double.MAX_VALUE);
        reconcileAttemptBtn.setVisible(false);
        reconcileAttemptBtn.setManaged(false);
        reconcileAttemptBtn.setOnAction(e -> reconcileLatestUncertain());

        ScrollPane settledScroll = new ScrollPane(paymentsListBox);
        settledScroll.setFitToWidth(true);
        VBox.setVgrow(settledScroll, Priority.ALWAYS);

        VBox leftPane = new VBox(10, balancesCard, form, quickRow, changeLabel, payBtn, reconcileAttemptBtn,
                new Label("Recorded Payments & Refunds"), settledScroll);
        leftPane.getStyleClass().add("pos-card");
        leftPane.setPrefWidth(470);
        HBox.setHgrow(leftPane, Priority.ALWAYS);

        // Right: Receipt Preview & Printing
        HBox printControls = new HBox(8, new Label("Language:"), languageCombo, generateReceiptBtn);
        printControls.setAlignment(Pos.CENTER_LEFT);
        generateReceiptBtn.setOnAction(e -> generateReceipt());

        ScrollPane receiptHistScroll = new ScrollPane(receiptsListBox);
        receiptHistScroll.setFitToWidth(true);
        receiptHistScroll.setPrefHeight(120);

        VBox rightPane = new VBox(10,
                new Label("Receipt & ESC/POS Preview"),
                printControls,
                receiptPreviewArea,
                new Label("Saved Receipts"),
                receiptHistScroll);
        rightPane.getStyleClass().add("pos-card");
        rightPane.setPrefWidth(430);
        VBox.setVgrow(receiptPreviewArea, Priority.ALWAYS);

        HBox body = new HBox(14, leftPane, rightPane);
        VBox.setVgrow(body, Priority.ALWAYS);

        SecondaryButton closeBtn = new SecondaryButton("Close");
        closeBtn.setOnAction(e -> stage.close());
        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, header, banner, body, new Separator(), footer);
        root.setPadding(new Insets(16));
        return root;
    }

    private void bumpTendered(BigDecimal delta) {
        BigDecimal cur = parseMoney(tenderedField.getText());
        tenderedField.setText(fmt(cur.add(delta)));
    }

    private void loadAllState() {
        String token = sessionContext.accessToken();
        UUID termId = sessionContext.profile() != null ? sessionContext.profile().terminalId() : null;
        Task<LoadedState> task = new Task<>() {
            @Override
            protected LoadedState call() throws Exception {
                List<PaymentMethodDto> methods = serverClient.listPaymentMethods(token).data();
                OrderPaymentSummaryDto summary = serverClient.getOrderPaymentSummary(token, orderId).data();
                CashSessionDto activeSess = null;
                try {
                    activeSess = serverClient.getActiveCashSession(token, termId).data();
                } catch (ApiClientException ignored) {
                }
                List<ReceiptDto> receipts = serverClient.listOrderReceipts(token, orderId).data();
                return new LoadedState(methods, summary, activeSess, receipts);
            }
        };
        task.setOnSucceeded(e -> {
            LoadedState s = task.getValue();
            methodCombo.getItems().setAll(s.methods());
            if (!s.methods().isEmpty() && methodCombo.getValue() == null) {
                methodCombo.setValue(s.methods().getFirst());
            }
            this.activeCashSession = s.activeSession();
            applySummary(s.summary(), s.receipts());
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void applySummary(OrderPaymentSummaryDto summary, List<ReceiptDto> receipts) {
        this.currentSummary = summary;
        if (receipts != null) {
            this.currentReceipts = receipts;
        }
        String curr = summary.currencyCode();
        orderTitleLabel.setText("Checkout — " + summary.orderNumber());
        orderStatusBadge.setText(summary.orderStatus());
        orderStatusBadge.setTone("PAID".equals(summary.orderStatus())
                ? StatusBadge.Tone.SUCCESS : StatusBadge.Tone.WARNING);

        if (activeCashSession != null) {
            sessionBadge.setText("Cash Session OPEN (" + curr + " " + fmt(activeCashSession.derivedExpectedCash()) + ")");
            sessionBadge.setTone(StatusBadge.Tone.SUCCESS);
        } else {
            sessionBadge.setText("No Open Cash Session");
            sessionBadge.setTone(StatusBadge.Tone.WARNING);
        }

        totalLabel.setText("Order Total: " + curr + " " + fmt(summary.orderTotal()));
        paidLabel.setText("Net Paid: " + curr + " " + fmt(summary.netPaidAmount())
                + (summary.totalRefundedAmount().compareTo(BigDecimal.ZERO) > 0
                ? " (Refunded: " + fmt(summary.totalRefundedAmount()) + ")" : ""));
        remainingLabel.setText("Remaining Due: " + curr + " " + fmt(summary.remainingDue()));

        String remText = fmt(summary.remainingDue());
        amountField.setText(remText);
        tenderedField.setText(remText);
        payBtn.setDisable(summary.remainingDue().compareTo(BigDecimal.ZERO) <= 0
                || !sessionContext.hasAnyPermission(PermissionCode.PAYMENTS_PROCESS, PermissionCode.PAYMENTS_CREATE));

        PaymentAttemptDto latest = summary.latestAttempt();
        boolean hasUncertain = latest != null && ("UNCERTAIN".equals(latest.status()) || "PROCESSING".equals(latest.status()));
        reconcileAttemptBtn.setVisible(hasUncertain);
        reconcileAttemptBtn.setManaged(hasUncertain);

        if (summary.hardwareNotice() != null && !summary.hardwareNotice().isBlank()) {
            banner.showError(summary.hardwareNotice());
        }

        renderPaymentHistory();
        renderReceiptsList();
        updateChangeReadout();
    }

    private void renderPaymentHistory() {
        paymentsListBox.getChildren().clear();
        if (currentSummary == null) {
            return;
        }
        String curr = currentSummary.currencyCode();
        boolean canRefund = sessionContext.hasPermission(PermissionCode.PAYMENTS_REFUND);

        for (PaymentDto p : currentSummary.payments()) {
            String desc = p.paymentMethodCode() + ": " + curr + " " + fmt(p.amount())
                    + (p.changeAmount().compareTo(BigDecimal.ZERO) > 0 ? " (Change " + fmt(p.changeAmount()) + ")" : "")
                    + (p.maskedAccount() != null ? " [" + p.maskedAccount() + "]" : "");
            Label lbl = new Label(desc);
            lbl.setWrapText(true);
            HBox row = new HBox(8, lbl);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(lbl, Priority.ALWAYS);

            BigDecimal refundable = p.amount().subtract(p.refundedAmount());
            if (canRefund && refundable.compareTo(BigDecimal.ZERO) > 0) {
                SecondaryButton refBtn = new SecondaryButton("Refund");
                refBtn.setOnAction(e -> promptRefund(p, refundable));
                row.getChildren().add(refBtn);
            }
            paymentsListBox.getChildren().add(row);
        }

        for (var r : currentSummary.refunds()) {
            Label lbl = new Label("REFUND (" + r.status() + "): -" + curr + " " + fmt(r.amount()) + " — " + r.reason());
            lbl.getStyleClass().add("FAILED".equals(r.status()) ? "pos-text-danger" : "pos-text-muted");
            paymentsListBox.getChildren().add(lbl);
        }
    }

    private void renderReceiptsList() {
        receiptsListBox.getChildren().clear();
        if (currentReceipts.isEmpty()) {
            receiptPreviewArea.setText("No receipts generated yet.");
            return;
        }
        receiptPreviewArea.setText(currentReceipts.getFirst().renderedText());
        for (ReceiptDto r : currentReceipts) {
            Label lbl = new Label(r.receiptNumber() + " (" + r.receiptType() + ") — " + r.lastPrintStatus() + " [prints: " + r.printCount() + "]");
            SecondaryButton viewBtn = new SecondaryButton("Preview");
            viewBtn.setOnAction(e -> receiptPreviewArea.setText(r.renderedText()));
            SecondaryButton reprintBtn = new SecondaryButton("Reprint");
            reprintBtn.setOnAction(e -> reprintReceipt(r.id()));
            HBox row = new HBox(8, lbl, viewBtn, reprintBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(lbl, Priority.ALWAYS);
            receiptsListBox.getChildren().add(row);
        }
    }

    private void updateMethodUiState() {
        PaymentMethodDto method = methodCombo.getValue();
        boolean isCash = method != null && "CASH".equalsIgnoreCase(method.methodType());
        tenderedField.setDisable(!isCash);
        if (!isCash) {
            tenderedField.setText(amountField.getText());
        }
        updateChangeReadout();
    }

    private void updateChangeReadout() {
        PaymentMethodDto method = methodCombo.getValue();
        boolean isCash = method != null && "CASH".equalsIgnoreCase(method.methodType());
        BigDecimal amt = parseMoney(amountField.getText());
        BigDecimal tend = isCash ? parseMoney(tenderedField.getText()) : amt;
        BigDecimal change = tend.subtract(amt).max(BigDecimal.ZERO);
        String curr = currentSummary != null ? currentSummary.currencyCode() : "";
        changeLabel.setText("Cash Change: " + curr + " " + fmt(change));
    }

    private void submitPayment() {
        PaymentMethodDto method = methodCombo.getValue();
        if (method == null || currentSummary == null) {
            return;
        }
        BigDecimal amt = parseMoney(amountField.getText());
        BigDecimal tendered = "CASH".equalsIgnoreCase(method.methodType()) ? parseMoney(tenderedField.getText()) : amt;
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            banner.showError("Enter a positive payment amount.");
            return;
        }

        banner.clear();
        payBtn.setDisable(true);
        String token = sessionContext.accessToken();
        String keyForAttempt = activeIdempotencyKey;
        ProcessPaymentRequest req = new ProcessPaymentRequest(
                orderId,
                method.id(),
                keyForAttempt,
                amt,
                tendered,
                BigDecimal.ZERO,
                activeCashSession != null ? activeCashSession.id() : null,
                null,
                null
        );

        Task<LoadedAfterAction> task = new Task<>() {
            @Override
            protected LoadedAfterAction call() throws Exception {
                OrderPaymentSummaryDto sum = serverClient.processPayment(token, req).data();
                List<ReceiptDto> rcps = serverClient.listOrderReceipts(token, orderId).data();
                UUID termId = sessionContext.profile() != null ? sessionContext.profile().terminalId() : null;
                CashSessionDto sess = null;
                try {
                    sess = serverClient.getActiveCashSession(token, termId).data();
                } catch (ApiClientException ignored) {
                }
                return new LoadedAfterAction(sum, rcps, sess);
            }
        };
        task.setOnSucceeded(e -> {
            LoadedAfterAction res = task.getValue();
            this.activeCashSession = res.session();
            this.activeIdempotencyKey = UUID.randomUUID().toString(); // Fresh key for next split tender
            applySummary(res.summary(), res.receipts());
            onPaymentChanged.run();

            PaymentAttemptDto latest = res.summary().latestAttempt();
            if (latest != null && "COMPLETED".equals(latest.status())) {
                banner.showSuccess("Payment confirmed by server (" + latest.providerName() + ").");
            } else if (latest != null && "UNCERTAIN".equals(latest.status())) {
                banner.showError("Terminal outcome UNCERTAIN. Query card status before retrying.");
            } else if (latest != null && "FAILED".equals(latest.status())) {
                banner.showError("Payment failed: " + latest.failureReason());
            }
        });
        task.setOnFailed(e -> {
            payBtn.setDisable(false);
            handleApiError(task.getException());
        });
        ioExecutor.submit(task);
    }

    private void reconcileLatestUncertain() {
        if (currentSummary == null || currentSummary.latestAttempt() == null) {
            return;
        }
        String token = sessionContext.accessToken();
        UUID attemptId = currentSummary.latestAttempt().id();
        Task<LoadedAfterAction> task = new Task<>() {
            @Override
            protected LoadedAfterAction call() throws Exception {
                OrderPaymentSummaryDto sum = serverClient.reconcileAttempt(token, attemptId).data();
                List<ReceiptDto> rcps = serverClient.listOrderReceipts(token, orderId).data();
                return new LoadedAfterAction(sum, rcps, activeCashSession);
            }
        };
        task.setOnSucceeded(e -> {
            LoadedAfterAction res = task.getValue();
            applySummary(res.summary(), res.receipts());
            onPaymentChanged.run();
            banner.showSuccess("Card terminal reconciliation completed.");
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void promptRefund(PaymentDto payment, BigDecimal maxRefundable) {
        Dialog<RefundRequest> dlg = new Dialog<>();
        dlg.setTitle("Issue Refund");
        dlg.setHeaderText("Refund from payment (" + payment.paymentMethodCode() + ", max " + fmt(maxRefundable) + ")");
        ButtonType confirmType = new ButtonType("Confirm Refund", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(confirmType, ButtonType.CANCEL);

        TextField refAmount = new TextField(fmt(maxRefundable));
        TextField reasonField = new TextField("Customer request");
        TextField supUserField = new TextField();
        supUserField.setPromptText("Supervisor username (if > threshold)");
        PasswordField supPassField = new PasswordField();
        supPassField.setPromptText("Supervisor password");

        GridPane gp = new GridPane();
        gp.setHgap(8);
        gp.setVgap(8);
        gp.add(new Label("Refund Amount:"), 0, 0);
        gp.add(refAmount, 1, 0);
        gp.add(new Label("Reason:"), 0, 1);
        gp.add(reasonField, 1, 1);
        gp.add(new Label("Supervisor User:"), 0, 2);
        gp.add(supUserField, 1, 2);
        gp.add(new Label("Supervisor Password:"), 0, 3);
        gp.add(supPassField, 1, 3);
        dlg.getDialogPane().setContent(gp);

        dlg.setResultConverter(btn -> {
            if (btn != confirmType) return null;
            BigDecimal amt = parseMoney(refAmount.getText());
            return new RefundRequest(
                    orderId, payment.id(), UUID.randomUUID().toString(), amt,
                    reasonField.getText(), null,
                    activeCashSession != null ? activeCashSession.id() : payment.cashSessionId(), null
            );
        });

        Optional<RefundRequest> result = dlg.showAndWait();
        if (result.isEmpty()) return;

        RefundRequest baseReq = result.get();
        String supUser = supUserField.getText() != null ? supUserField.getText().trim() : "";
        String supPass = supPassField.getText() != null ? supPassField.getText() : "";
        String token = sessionContext.accessToken();

        Task<LoadedAfterAction> task = new Task<>() {
            @Override
            protected LoadedAfterAction call() throws Exception {
                UUID approvalId = null;
                if (!supUser.isEmpty() && !supPass.isEmpty()) {
                    approvalId = serverClient.authorizeSupervisorAction(token, new SupervisorApprovalRequest(
                            supUser, supPass, PermissionCode.PAYMENTS_REFUND.code(),
                            "Payment", payment.id().toString(), baseReq.reason(), null
                    )).data().approvalId();
                }
                RefundRequest finalReq = new RefundRequest(
                        baseReq.orderId(), baseReq.originalPaymentId(), baseReq.idempotencyKey(),
                        baseReq.amount(), baseReq.reason(), approvalId, baseReq.cashSessionId(), null
                );
                OrderPaymentSummaryDto sum = serverClient.issueRefund(token, finalReq).data();
                List<ReceiptDto> rcps = serverClient.listOrderReceipts(token, orderId).data();
                return new LoadedAfterAction(sum, rcps, activeCashSession);
            }
        };
        task.setOnSucceeded(e -> {
            LoadedAfterAction res = task.getValue();
            applySummary(res.summary(), res.receipts());
            onPaymentChanged.run();
            banner.showSuccess("Refund recorded.");
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void generateReceipt() {
        String token = sessionContext.accessToken();
        GenerateReceiptRequest req = new GenerateReceiptRequest(languageCombo.getValue(), 42, "UTF-8", true);
        Task<List<ReceiptDto>> task = new Task<>() {
            @Override
            protected List<ReceiptDto> call() throws Exception {
                serverClient.generateOrderReceipt(token, orderId, req);
                return serverClient.listOrderReceipts(token, orderId).data();
            }
        };
        task.setOnSucceeded(e -> {
            this.currentReceipts = task.getValue();
            renderReceiptsList();
            banner.showSuccess("Receipt generated (" + currentReceipts.getFirst().lastPrintStatus() + ").");
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void reprintReceipt(UUID receiptId) {
        String token = sessionContext.accessToken();
        Task<List<ReceiptDto>> task = new Task<>() {
            @Override
            protected List<ReceiptDto> call() throws Exception {
                serverClient.reprintReceipt(token, receiptId);
                return serverClient.listOrderReceipts(token, orderId).data();
            }
        };
        task.setOnSucceeded(e -> {
            this.currentReceipts = task.getValue();
            renderReceiptsList();
            banner.showSuccess("Duplicate receipt reprinted (" + currentReceipts.getFirst().lastPrintStatus() + ").");
        });
        task.setOnFailed(e -> handleApiError(task.getException()));
        ioExecutor.submit(task);
    }

    private void handleApiError(Throwable ex) {
        if (ex instanceof ApiClientException ace && ace.statusCode() == 401) {
            onSessionExpired.run();
            stage.close();
            return;
        }
        banner.showError(ex != null ? ex.getMessage() : "Operation failed.");
    }

    private static BigDecimal parseMoney(String raw) {
        try {
            if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
            return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String fmt(BigDecimal val) {
        return (val != null ? val : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record LoadedState(
            List<PaymentMethodDto> methods,
            OrderPaymentSummaryDto summary,
            CashSessionDto activeSession,
            List<ReceiptDto> receipts
    ) {
    }

    private record LoadedAfterAction(
            OrderPaymentSummaryDto summary,
            List<ReceiptDto> receipts,
            CashSessionDto session
    ) {
    }
}
