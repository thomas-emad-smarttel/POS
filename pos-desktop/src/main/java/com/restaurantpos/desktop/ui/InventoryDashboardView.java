package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.StatusBadge;
import com.restaurantpos.shared.dto.inventory.InventoryAlertDto;
import com.restaurantpos.shared.dto.inventory.InventoryBalanceDto;
import com.restaurantpos.shared.dto.inventory.InventoryValuationReportDto;
import com.restaurantpos.shared.dto.inventory.ResolveInventoryAlertRequest;
import com.restaurantpos.shared.dto.inventory.StockMovementDto;
import com.restaurantpos.shared.security.PermissionCode;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Inventory Dashboard & Monitoring screen: stock KPIs (OnHand, Reserved, Available, Valuation),
 * balance ledger overview, append-only movement audit stream, and low-stock/variance alerts.
 */
public final class InventoryDashboardView extends VBox {

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final StatusBadge valuationBadge = new StatusBadge("Valuation: -", StatusBadge.Tone.NEUTRAL);
    private final StatusBadge lowStockBadge = new StatusBadge("Low Stock: 0", StatusBadge.Tone.SUCCESS);
    private final StatusBadge alertCountBadge = new StatusBadge("Alerts: 0", StatusBadge.Tone.SUCCESS);

    private final ListView<String> balanceList = new ListView<>();
    private final ListView<String> movementList = new ListView<>();
    private final VBox alertsBox = new VBox(8);

    public InventoryDashboardView(
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

        Label title = new Label("Inventory Dashboard, Balances & Alerts");
        title.getStyleClass().add("pos-heading-lg");

        SecondaryButton refreshBtn = new SecondaryButton("Refresh");
        refreshBtn.setOnAction(e -> loadData());

        HBox header = new HBox(12, title, valuationBadge, lowStockBadge, alertCountBadge, refreshBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        Tab balancesTab = new Tab("Balances (OnHand / Reserved / Available)", balanceList);
        balancesTab.setClosable(false);

        Tab movementsTab = new Tab("Stock Movement Ledger", movementList);
        movementsTab.setClosable(false);

        Tab alertsTab = new Tab("Alerts & Low-Stock Thresholds", alertsBox);
        alertsTab.setClosable(false);

        TabPane tabs = new TabPane(balancesTab, movementsTab, alertsTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        getChildren().addAll(header, banner, tabs);
        loadData();
    }

    public void loadData() {
        String token = sessionContext.accessToken();
        if (token == null) {
            return;
        }
        Task<DashboardSnapshot> task = new Task<>() {
            @Override
            protected DashboardSnapshot call() throws Exception {
                List<InventoryBalanceDto> balances = serverClient.listInventoryBalances(token).data();
                List<StockMovementDto> movements = serverClient.listStockMovements(token, null).data();
                List<InventoryAlertDto> alerts = serverClient.listInventoryAlerts(token, true).data();
                InventoryValuationReportDto report = sessionContext.hasPermission(PermissionCode.INVENTORY_COST_VIEW)
                        ? serverClient.getValuationReport(token).data()
                        : serverClient.getLowStockReport(token).data();
                return new DashboardSnapshot(balances, movements, alerts, report);
            }
        };
        task.setOnSucceeded(e -> render(task.getValue()));
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void render(DashboardSnapshot snap) {
        if (snap.report() != null) {
            String valText = snap.report().totalOnHandValuation() != null
                    ? "OnHand Valuation: $" + snap.report().totalOnHandValuation()
                    : "Valuation: Restricted";
            valuationBadge.setText(valText);
            int low = snap.report().lowStockCount() + snap.report().outOfStockCount();
            lowStockBadge.setText("Low/Out: " + low);
            lowStockBadge.setTone(low > 0 ? StatusBadge.Tone.WARNING : StatusBadge.Tone.SUCCESS);
        }

        balanceList.getItems().setAll(snap.balances().stream()
                .map(b -> String.format("[%s] %s (%s) | OnHand: %s | Reserved: %s | Avail: %s | Reorder: %s%s",
                        b.locationCode() != null ? b.locationCode() : "-",
                        b.itemName() != null ? b.itemName() : b.inventoryItemId(),
                        b.unitCode(),
                        b.onHandQuantity(), b.reservedQuantity(), b.availableQuantity(),
                        b.reorderLevel(),
                        b.valuationAmount() != null ? " | Val: $" + b.valuationAmount() : ""))
                .toList());

        movementList.getItems().setAll(snap.movements().stream()
                .map(m -> String.format("%s | %s (%s) %s %s | Before: %s -> After: %s | %s",
                        m.occurredAt(), m.itemSku(), m.movementType(), m.direction(),
                        m.quantityDelta(), m.balanceBefore(), m.balanceAfter(),
                        m.reason() != null ? m.reason() : ""))
                .toList());

        alertsBox.getChildren().clear();
        alertCountBadge.setText("Active Alerts: " + snap.alerts().size());
        alertCountBadge.setTone(snap.alerts().isEmpty() ? StatusBadge.Tone.SUCCESS : StatusBadge.Tone.DANGER);

        for (InventoryAlertDto alert : snap.alerts()) {
            HBox row = new HBox(10);
            row.getStyleClass().add("pos-card");
            row.setPadding(new Insets(10));
            row.setAlignment(Pos.CENTER_LEFT);
            Label msg = new Label(String.format("[%s / %s] %s", alert.alertType(), alert.severity(), alert.message()));
            HBox.setHgrow(msg, Priority.ALWAYS);
            PrimaryButton resolveBtn = new PrimaryButton("Resolve");
            resolveBtn.setOnAction(ev -> resolveAlert(alert));
            row.getChildren().addAll(msg, resolveBtn);
            alertsBox.getChildren().add(row);
        }
        if (snap.alerts().isEmpty()) {
            alertsBox.getChildren().add(new Label("No active inventory alerts in this branch."));
        }
    }

    private void resolveAlert(InventoryAlertDto alert) {
        String token = sessionContext.accessToken();
        if (token == null) return;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                serverClient.resolveInventoryAlert(token, alert.id(),
                        new ResolveInventoryAlertRequest(alert.version(), "RESOLVED", "Reviewed on desktop"));
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Alert resolved");
            loadData();
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void handleFailure(Throwable ex) {
        if (ex instanceof ApiClientException ace && ace.statusCode() == 401) {
            onSessionExpired.run();
            return;
        }
        banner.showError(ex != null ? ex.getMessage() : "Inventory dashboard refresh failed");
    }

    private record DashboardSnapshot(
            List<InventoryBalanceDto> balances,
            List<StockMovementDto> movements,
            List<InventoryAlertDto> alerts,
            InventoryValuationReportDto report
    ) {
    }
}
