package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.shared.dto.inventory.CreateStockCountRequest;
import com.restaurantpos.shared.dto.inventory.CreateStockTransferRequest;
import com.restaurantpos.shared.dto.inventory.InventoryItemDto;
import com.restaurantpos.shared.dto.inventory.InventoryLocationDto;
import com.restaurantpos.shared.dto.inventory.ReceiveStockTransferRequest;
import com.restaurantpos.shared.dto.inventory.RecordStockAdjustmentRequest;
import com.restaurantpos.shared.dto.inventory.RecordWasteRequest;
import com.restaurantpos.shared.dto.inventory.StockCountActionRequest;
import com.restaurantpos.shared.dto.inventory.StockCountDto;
import com.restaurantpos.shared.dto.inventory.StockTransferActionRequest;
import com.restaurantpos.shared.dto.inventory.StockTransferDto;
import com.restaurantpos.shared.dto.inventory.StockWasteRecordDto;
import com.restaurantpos.shared.dto.inventory.UnitOfMeasureDto;
import com.restaurantpos.shared.dto.inventory.UpdateStockCountItemsRequest;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Operational stock control screen covering Waste Recording, Manual Adjustments,
 * Physical Stock Counts (Draft -> Submit -> Approve -> Post), and Inter/Intra-Branch Transfers.
 */
public final class StockOperationsView extends VBox {

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final ComboBox<InventoryLocationDto> locCombo = new ComboBox<>();
    private final ComboBox<InventoryItemDto> itemCombo = new ComboBox<>();
    private final ComboBox<UnitOfMeasureDto> unitCombo = new ComboBox<>();

    private final ListView<String> wasteList = new ListView<>();
    private final ListView<StockCountDto> countList = new ListView<>();
    private final ListView<StockTransferDto> transferList = new ListView<>();

    public StockOperationsView(
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

        Label title = new Label("Stock Adjustments, Waste, Physical Counts & Transfers");
        title.getStyleClass().add("pos-heading-lg");
        SecondaryButton refreshBtn = new SecondaryButton("Refresh");
        refreshBtn.setOnAction(e -> loadData());
        HBox header = new HBox(12, title, refreshBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox sharedPickers = new HBox(8, locCombo, itemCombo, unitCombo);
        TabPane tabs = new TabPane(buildWasteAndAdjustmentTab(), buildCountsTab(), buildTransfersTab());
        VBox.setVgrow(tabs, Priority.ALWAYS);

        getChildren().addAll(header, banner, sharedPickers, tabs);
        loadData();
    }

    private Tab buildWasteAndAdjustmentTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(8));

        TextField qtyField = new TextField("1.00");
        qtyField.setPromptText("Qty");
        TextField costField = new TextField("5.00");
        costField.setPromptText("Unit Cost");
        TextField reasonField = new TextField();
        reasonField.setPromptText("Reason / Notes");

        ComboBox<String> wasteReason = new ComboBox<>();
        wasteReason.getItems().addAll("PREPARATION_WASTE", "SPOILAGE", "EXPIRED", "DAMAGED",
                "INCORRECT_PREPARATION", "CUSTOMER_RETURN_DISPOSAL", "OTHER");
        wasteReason.setValue("PREPARATION_WASTE");

        PrimaryButton wasteBtn = new PrimaryButton("Record Waste");
        wasteBtn.setOnAction(e -> {
            if (locCombo.getValue() == null || itemCombo.getValue() == null || unitCombo.getValue() == null) {
                banner.showError("Select Location, Item & Unit");
                return;
            }
            runOp("Waste recorded", () -> serverClient.recordWaste(sessionContext.accessToken(),
                    new RecordWasteRequest(locCombo.getValue().id(), itemCombo.getValue().id(),
                            unitCombo.getValue().id(), new BigDecimal(qtyField.getText().trim()),
                            wasteReason.getValue(), reasonField.getText(), null,
                            "ui-waste-" + UUID.randomUUID(), null)));
        });

        ComboBox<String> adjType = new ComboBox<>();
        adjType.getItems().addAll("OPENING_BALANCE", "STOCK_ADJUSTMENT_IN", "STOCK_ADJUSTMENT_OUT", "DAMAGE");
        adjType.setValue("OPENING_BALANCE");
        PrimaryButton adjBtn = new PrimaryButton("Post Adjustment");
        adjBtn.setOnAction(e -> {
            if (locCombo.getValue() == null || itemCombo.getValue() == null || unitCombo.getValue() == null) {
                banner.showError("Select Location, Item & Unit");
                return;
            }
            runOp("Adjustment posted", () -> serverClient.recordStockAdjustment(sessionContext.accessToken(),
                    new RecordStockAdjustmentRequest(locCombo.getValue().id(), itemCombo.getValue().id(),
                            unitCombo.getValue().id(), new BigDecimal(qtyField.getText().trim()),
                            adjType.getValue(), new BigDecimal(costField.getText().trim()),
                            reasonField.getText().isBlank() ? "Desktop adjustment" : reasonField.getText(),
                            null, null, "ui-adj-" + UUID.randomUUID(), null, false)));
        });

        box.getChildren().addAll(
                new HBox(8, qtyField, costField, reasonField),
                new HBox(8, wasteReason, wasteBtn, adjType, adjBtn),
                wasteList
        );
        VBox.setVgrow(wasteList, Priority.ALWAYS);
        Tab tab = new Tab("Waste & Adjustments", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildCountsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(8));

        PrimaryButton createCountBtn = new PrimaryButton("New Count Session (Selected Location)");
        createCountBtn.setOnAction(e -> {
            if (locCombo.getValue() == null) {
                banner.showError("Select a location first");
                return;
            }
            runOp("Stock count created", () -> serverClient.createStockCount(sessionContext.accessToken(),
                    new CreateStockCountRequest(locCombo.getValue().id(), null, List.of(), "UI Physical Count")));
        });

        TextField countedQtyField = new TextField("10.00");
        countedQtyField.setPromptText("Counted Qty (Selected Item)");
        SecondaryButton captureBtn = new SecondaryButton("Capture Qty");
        captureBtn.setOnAction(e -> {
            StockCountDto sc = countList.getSelectionModel().getSelectedItem();
            InventoryItemDto item = itemCombo.getValue();
            if (sc == null || item == null) {
                banner.showError("Select a count session and inventory item");
                return;
            }
            runOp("Count captured", () -> serverClient.updateStockCountItems(sessionContext.accessToken(), sc.id(),
                    new UpdateStockCountItemsRequest(sc.version(), List.of(
                            new UpdateStockCountItemsRequest.CountLineInput(
                                    item.id(), new BigDecimal(countedQtyField.getText().trim()), false, null)))));
        });

        SecondaryButton submitBtn = new SecondaryButton("Submit");
        submitBtn.setOnAction(e -> {
            StockCountDto sc = countList.getSelectionModel().getSelectedItem();
            if (sc != null) runOp("Submitted", () -> serverClient.submitStockCount(
                    sessionContext.accessToken(), sc.id(), new StockCountActionRequest(sc.version(), null, null)));
        });
        SecondaryButton approveBtn = new SecondaryButton("Approve");
        approveBtn.setOnAction(e -> {
            StockCountDto sc = countList.getSelectionModel().getSelectedItem();
            if (sc != null) runOp("Approved", () -> serverClient.approveStockCount(
                    sessionContext.accessToken(), sc.id(), new StockCountActionRequest(sc.version(), null, null)));
        });
        PrimaryButton postBtn = new PrimaryButton("Post Variances");
        postBtn.setOnAction(e -> {
            StockCountDto sc = countList.getSelectionModel().getSelectedItem();
            if (sc != null) runOp("Posted to Ledger", () -> serverClient.postStockCount(
                    sessionContext.accessToken(), sc.id(), new StockCountActionRequest(sc.version(), null, null)));
        });

        box.getChildren().addAll(
                new HBox(8, createCountBtn, countedQtyField, captureBtn, submitBtn, approveBtn, postBtn),
                countList
        );
        VBox.setVgrow(countList, Priority.ALWAYS);
        Tab tab = new Tab("Stock Counts", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildTransfersTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(8));

        ComboBox<InventoryLocationDto> destLocCombo = new ComboBox<>();
        destLocCombo.setPromptText("Destination Location");
        destLocCombo.itemsProperty().bind(locCombo.itemsProperty());
        TextField trfQtyField = new TextField("5.00");
        trfQtyField.setPromptText("Qty");

        PrimaryButton reqBtn = new PrimaryButton("Request Transfer");
        reqBtn.setOnAction(e -> {
            if (locCombo.getValue() == null || destLocCombo.getValue() == null
                    || itemCombo.getValue() == null || unitCombo.getValue() == null) {
                banner.showError("Select source, destination, item & unit");
                return;
            }
            runOp("Transfer requested", () -> serverClient.createStockTransfer(sessionContext.accessToken(),
                    new CreateStockTransferRequest(locCombo.getValue().id(),
                            destLocCombo.getValue().branchId(), destLocCombo.getValue().id(),
                            "Desktop transfer",
                            List.of(new CreateStockTransferRequest.TransferLineInput(
                                    itemCombo.getValue().id(), unitCombo.getValue().id(),
                                    new BigDecimal(trfQtyField.getText().trim()), null)))));
        });

        SecondaryButton approveBtn = new SecondaryButton("Approve");
        approveBtn.setOnAction(e -> {
            StockTransferDto trf = transferList.getSelectionModel().getSelectedItem();
            if (trf != null) runOp("Approved", () -> serverClient.approveStockTransfer(
                    sessionContext.accessToken(), trf.id(), new StockTransferActionRequest(trf.version(), null)));
        });
        SecondaryButton dispatchBtn = new SecondaryButton("Dispatch");
        dispatchBtn.setOnAction(e -> {
            StockTransferDto trf = transferList.getSelectionModel().getSelectedItem();
            if (trf != null) runOp("Dispatched", () -> serverClient.dispatchStockTransfer(
                    sessionContext.accessToken(), trf.id(), new StockTransferActionRequest(trf.version(), null)));
        });
        PrimaryButton receiveAllBtn = new PrimaryButton("Receive All");
        receiveAllBtn.setOnAction(e -> {
            StockTransferDto trf = transferList.getSelectionModel().getSelectedItem();
            if (trf == null || trf.items().isEmpty()) return;
            var lines = trf.items().stream()
                    .map(ti -> new ReceiveStockTransferRequest.ReceiveLineInput(
                            ti.id(), ti.dispatchedQuantity().subtract(ti.receivedQuantity()),
                            BigDecimal.ZERO, BigDecimal.ZERO, "Full receipt"))
                    .toList();
            runOp("Received", () -> serverClient.receiveStockTransfer(sessionContext.accessToken(), trf.id(),
                    new ReceiveStockTransferRequest(trf.version(), lines, null)));
        });

        box.getChildren().addAll(
                new HBox(8, destLocCombo, trfQtyField, reqBtn, approveBtn, dispatchBtn, receiveAllBtn),
                transferList
        );
        VBox.setVgrow(transferList, Priority.ALWAYS);
        Tab tab = new Tab("Branch Transfers", box);
        tab.setClosable(false);
        return tab;
    }

    public void loadData() {
        String token = sessionContext.accessToken();
        if (token == null) return;
        Task<OpsSnap> task = new Task<>() {
            @Override
            protected OpsSnap call() throws Exception {
                return new OpsSnap(
                        serverClient.listInventoryLocations(token).data(),
                        serverClient.listInventoryItems(token).data(),
                        serverClient.listInventoryUnits(token).data(),
                        serverClient.listWasteRecords(token).data(),
                        serverClient.listStockCounts(token).data(),
                        serverClient.listStockTransfers(token).data()
                );
            }
        };
        task.setOnSucceeded(e -> {
            OpsSnap s = task.getValue();
            locCombo.getItems().setAll(s.locations());
            itemCombo.getItems().setAll(s.items());
            unitCombo.getItems().setAll(s.units());
            wasteList.getItems().setAll(s.waste().stream()
                    .map(w -> String.format("%s | %s (%s) -%s %s [%s]",
                            w.occurredAt(), w.itemName(), w.reasonCode(), w.originalQuantity(), w.unitCode(), w.notes()))
                    .toList());
            countList.getItems().setAll(s.counts());
            transferList.getItems().setAll(s.transfers());
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    @FunctionalInterface
    private interface RemoteCall {
        void run() throws Exception;
    }

    private void runOp(String okMsg, RemoteCall call) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                call.run();
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess(okMsg);
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
        banner.showError(ex != null ? ex.getMessage() : "Stock operation failed");
    }

    private record OpsSnap(
            List<InventoryLocationDto> locations,
            List<InventoryItemDto> items,
            List<UnitOfMeasureDto> units,
            List<StockWasteRecordDto> waste,
            List<StockCountDto> counts,
            List<StockTransferDto> transfers
    ) {
    }
}
