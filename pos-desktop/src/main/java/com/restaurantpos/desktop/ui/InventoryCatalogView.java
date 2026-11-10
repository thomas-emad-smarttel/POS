package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.shared.dto.inventory.InventoryCategoryDto;
import com.restaurantpos.shared.dto.inventory.InventoryItemDto;
import com.restaurantpos.shared.dto.inventory.InventoryLocationDto;
import com.restaurantpos.shared.dto.inventory.SaveInventoryCategoryRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryItemRequest;
import com.restaurantpos.shared.dto.inventory.SaveInventoryLocationRequest;
import com.restaurantpos.shared.dto.inventory.SaveUnitConversionRequest;
import com.restaurantpos.shared.dto.inventory.SaveUnitRequest;
import com.restaurantpos.shared.dto.inventory.UnitConversionDto;
import com.restaurantpos.shared.dto.inventory.UnitOfMeasureDto;
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
import java.util.concurrent.ExecutorService;

/**
 * Inventory catalog setup view for items, categories, branch storage locations, units, and conversions.
 */
public final class InventoryCatalogView extends VBox {

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final ListView<String> itemsList = new ListView<>();
    private final ListView<String> categoriesList = new ListView<>();
    private final ListView<String> locationsList = new ListView<>();
    private final ListView<String> unitsList = new ListView<>();

    private final ComboBox<UnitOfMeasureDto> itemUnitCombo = new ComboBox<>();
    private final ComboBox<InventoryCategoryDto> itemCategoryCombo = new ComboBox<>();
    private final ComboBox<InventoryLocationDto> itemLocCombo = new ComboBox<>();
    private final ComboBox<UnitOfMeasureDto> convFromUnitCombo = new ComboBox<>();
    private final ComboBox<UnitOfMeasureDto> convToUnitCombo = new ComboBox<>();

    public InventoryCatalogView(
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

        Label title = new Label("Inventory Catalog, Locations & Units");
        title.getStyleClass().add("pos-heading-lg");
        SecondaryButton refreshBtn = new SecondaryButton("Refresh");
        refreshBtn.setOnAction(e -> loadData());
        HBox header = new HBox(12, title, refreshBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        TabPane tabs = new TabPane(buildItemsTab(), buildCategoriesLocationsTab(), buildUnitsTab());
        VBox.setVgrow(tabs, Priority.ALWAYS);

        getChildren().addAll(header, banner, tabs);
        loadData();
    }

    private Tab buildItemsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(8));

        TextField skuField = new TextField();
        skuField.setPromptText("SKU (e.g. ING-BEEF)");
        TextField nameField = new TextField();
        nameField.setPromptText("Item Name");
        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("RAW_INGREDIENT", "PREPARED_INGREDIENT", "SEMI_FINISHED",
                "FINISHED_GOOD", "PACKAGING", "CONSUMABLE", "OTHER");
        typeBox.setValue("RAW_INGREDIENT");
        TextField reorderField = new TextField("10.00");
        reorderField.setPromptText("Reorder Level");
        TextField costField = new TextField("5.00");
        costField.setPromptText("Initial Avg Cost");

        PrimaryButton addBtn = new PrimaryButton("Add Item");
        addBtn.setOnAction(e -> {
            UnitOfMeasureDto unit = itemUnitCombo.getValue();
            if (unit == null) {
                banner.showError("Select a base unit first");
                return;
            }
            SaveInventoryItemRequest req = new SaveInventoryItemRequest(
                    itemCategoryCombo.getValue() != null ? itemCategoryCombo.getValue().id() : null,
                    itemLocCombo.getValue() != null ? itemLocCombo.getValue().id() : null,
                    false, skuField.getText(), nameField.getText(), null, typeBox.getValue(), null,
                    unit.id(), BigDecimal.ZERO, new BigDecimal(reorderField.getText().trim()),
                    new BigDecimal(costField.getText().trim()), "STANDARD", false, false,
                    "INHERIT", "WEIGHTED_AVERAGE", true
            );
            runMutation("Item created", () -> serverClient.createInventoryItem(sessionContext.accessToken(), req));
        });

        HBox form = new HBox(8, skuField, nameField, typeBox, itemUnitCombo, itemCategoryCombo, itemLocCombo, reorderField, costField, addBtn);
        box.getChildren().addAll(form, itemsList);
        VBox.setVgrow(itemsList, Priority.ALWAYS);

        Tab tab = new Tab("Inventory Items", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildCategoriesLocationsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(8));

        TextField catCode = new TextField();
        catCode.setPromptText("Category Code");
        TextField catName = new TextField();
        catName.setPromptText("Category Name");
        PrimaryButton addCat = new PrimaryButton("Create Category");
        addCat.setOnAction(e -> runMutation("Category saved", () ->
                serverClient.createInventoryCategory(sessionContext.accessToken(),
                        new SaveInventoryCategoryRequest(catCode.getText(), catName.getText(), null, 1, true))));

        TextField locCode = new TextField();
        locCode.setPromptText("Location Code");
        TextField locName = new TextField();
        locName.setPromptText("Location Name");
        ComboBox<String> locType = new ComboBox<>();
        locType.getItems().addAll("MAIN_STORE", "COLD_ROOM", "FREEZER", "DRY_STORAGE", "BAR", "PREP_LINE", "OTHER");
        locType.setValue("MAIN_STORE");
        PrimaryButton addLoc = new PrimaryButton("Create Location");
        addLoc.setOnAction(e -> runMutation("Location saved", () ->
                serverClient.createInventoryLocation(sessionContext.accessToken(),
                        new SaveInventoryLocationRequest(locCode.getText(), locName.getText(), locType.getValue(), false, true))));

        box.getChildren().addAll(
                new HBox(8, catCode, catName, addCat),
                categoriesList,
                new HBox(8, locCode, locName, locType, addLoc),
                locationsList
        );
        Tab tab = new Tab("Categories & Locations", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab buildUnitsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(8));

        TextField unitCode = new TextField();
        unitCode.setPromptText("Code (e.g. KG, G, BOX)");
        TextField unitName = new TextField();
        unitName.setPromptText("Name");
        ComboBox<String> unitType = new ComboBox<>();
        unitType.getItems().addAll("WEIGHT", "VOLUME", "COUNT");
        unitType.setValue("WEIGHT");
        PrimaryButton addUnit = new PrimaryButton("Add Unit");
        addUnit.setOnAction(e -> runMutation("Unit created", () ->
                serverClient.createInventoryUnit(sessionContext.accessToken(),
                        new SaveUnitRequest(unitCode.getText(), unitName.getText(), unitType.getValue(), null, BigDecimal.ONE))));

        TextField factorField = new TextField("1000");
        factorField.setPromptText("Factor");
        PrimaryButton addConv = new PrimaryButton("Add Conversion");
        addConv.setOnAction(e -> {
            if (convFromUnitCombo.getValue() == null || convToUnitCombo.getValue() == null) {
                banner.showError("Choose From & To units");
                return;
            }
            runMutation("Conversion saved", () -> serverClient.saveUnitConversion(sessionContext.accessToken(),
                    new SaveUnitConversionRequest(null, convFromUnitCombo.getValue().id(),
                            convToUnitCombo.getValue().id(), new BigDecimal(factorField.getText().trim()), true)));
        });

        box.getChildren().addAll(
                new HBox(8, unitCode, unitName, unitType, addUnit),
                new HBox(8, new Label("1"), convFromUnitCombo, new Label("="), factorField, convToUnitCombo, addConv),
                unitsList
        );
        VBox.setVgrow(unitsList, Priority.ALWAYS);
        Tab tab = new Tab("Units & Conversions", box);
        tab.setClosable(false);
        return tab;
    }

    public void loadData() {
        String token = sessionContext.accessToken();
        if (token == null) return;
        Task<CatalogSnap> task = new Task<>() {
            @Override
            protected CatalogSnap call() throws Exception {
                return new CatalogSnap(
                        serverClient.listInventoryItems(token).data(),
                        serverClient.listInventoryCategories(token).data(),
                        serverClient.listInventoryLocations(token).data(),
                        serverClient.listInventoryUnits(token).data(),
                        serverClient.listUnitConversions(token).data()
                );
            }
        };
        task.setOnSucceeded(e -> {
            CatalogSnap s = task.getValue();
            itemsList.getItems().setAll(s.items().stream()
                    .map(i -> String.format("[%s] %s (%s) | Type: %s | Reorder: %s%s",
                            i.sku(), i.name(), i.unitCode(), i.itemType(), i.reorderLevel(),
                            i.averageCost() != null ? " | Avg Cost: $" + i.averageCost() : ""))
                    .toList());
            categoriesList.getItems().setAll(s.categories().stream()
                    .map(c -> c.code() + " - " + c.name())
                    .toList());
            locationsList.getItems().setAll(s.locations().stream()
                    .map(l -> l.code() + " - " + l.name() + " [" + l.locationType() + "]")
                    .toList());

            List<String> unitLines = new java.util.ArrayList<>(s.units().stream()
                    .map(u -> u.code() + " (" + u.name() + ", " + u.unitType() + ")")
                    .toList());
            s.conversions().forEach(c -> unitLines.add(
                    "CONV: 1 " + c.fromUnitCode() + " = " + c.conversionFactor() + " " + c.toUnitCode()));
            unitsList.getItems().setAll(unitLines);

            itemUnitCombo.getItems().setAll(s.units());
            convFromUnitCombo.getItems().setAll(s.units());
            convToUnitCombo.getItems().setAll(s.units());
            itemCategoryCombo.getItems().setAll(s.categories());
            itemLocCombo.getItems().setAll(s.locations());
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    @FunctionalInterface
    private interface RemoteCall {
        void run() throws Exception;
    }

    private void runMutation(String msg, RemoteCall call) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                call.run();
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess(msg);
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
        banner.showError(ex != null ? ex.getMessage() : "Catalog request failed");
    }

    private record CatalogSnap(
            List<InventoryItemDto> items,
            List<InventoryCategoryDto> categories,
            List<InventoryLocationDto> locations,
            List<UnitOfMeasureDto> units,
            List<UnitConversionDto> conversions
    ) {
    }
}
