package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.SelectBoxes;
import com.restaurantpos.shared.dto.inventory.InventoryItemDto;
import com.restaurantpos.shared.dto.inventory.RecipeCostPreviewDto;
import com.restaurantpos.shared.dto.inventory.RecipeDto;
import com.restaurantpos.shared.dto.inventory.SaveRecipeRequest;
import com.restaurantpos.shared.dto.inventory.UnitOfMeasureDto;
import com.restaurantpos.shared.dto.pos.ProductDto;
import com.restaurantpos.shared.security.PermissionCode;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Versioned Recipe & Cost Editor supporting ingredients, sub-recipes, units,
 * waste loss %, prep loss %, preparation notes, modifier mappings, and theoretical cost preview.
 */
public final class RecipeEditorView extends VBox {

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final ListView<RecipeDto> recipesList = new ListView<>();
    private final ListView<String> costBreakdownList = new ListView<>();
    private final Label costSummaryLabel = new Label("Select a recipe to view versions and cost preview.");

    private final ComboBox<ProductDto> productCombo = new ComboBox<>();
    private final ComboBox<InventoryItemDto> ingredientCombo = new ComboBox<>();
    private final ComboBox<RecipeDto> subRecipeCombo = new ComboBox<>();
    private final ComboBox<UnitOfMeasureDto> unitCombo = new ComboBox<>();
    private final List<SaveRecipeRequest.IngredientLineInput> pendingLines = new ArrayList<>();
    private final List<SaveRecipeRequest.ModifierMappingInput> pendingModMappings = new ArrayList<>();
    private final ListView<String> draftLinesList = new ListView<>();

    public RecipeEditorView(
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

        Label title = new Label("Recipe Management, Versioning & Cost Preview");
        title.getStyleClass().add("pos-heading-lg");
        SecondaryButton refreshBtn = new SecondaryButton("Refresh");
        refreshBtn.setOnAction(e -> loadData());
        HBox header = new HBox(12, title, refreshBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        SelectBoxes.configure(productCombo, "Link Menu Product...", p -> p.name() + " (" + p.sku() + ")");
        SelectBoxes.configure(ingredientCombo, "Select Ingredient...", i -> i.name() + " [" + i.sku() + "]");
        SelectBoxes.configure(subRecipeCombo, "Or Sub-Recipe...", r -> r.recipeName() + " (v" + r.activeVersionNumber() + ")");
        SelectBoxes.configure(unitCombo, "Unit...", u -> u.code() + " — " + u.name());

        recipesList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(RecipeDto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%s | v%d%s",
                            item.recipeName(),
                            item.activeVersionNumber(),
                            item.theoreticalCost() != null ? " | Cost: $" + item.theoreticalCost()
                                    + " (" + item.foodCostPercent() + "%)" : ""));
                }
            }
        });
        recipesList.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) loadCostPreview(sel);
        });

        TextField recipeNameField = new TextField();
        recipeNameField.setPromptText("Recipe Name");
        TextField yieldField = new TextField("1.00");
        yieldField.setPromptText("Yield Qty");
        TextField lossField = new TextField("0.00");
        lossField.setPromptText("Prep Loss %");
        CheckBox subRecipeCheck = new CheckBox("Sub-Recipe");
        TextArea prepNotes = new TextArea();
        prepNotes.setPromptText("Preparation instructions...");
        prepNotes.setPrefRowCount(2);

        TextField qtyField = new TextField("0.150");
        qtyField.setPromptText("Qty");
        TextField wastePctField = new TextField("0");
        wastePctField.setPromptText("Line Loss %");
        SecondaryButton addIngBtn = new SecondaryButton("+ Add Ingredient / Sub-Recipe");
        addIngBtn.setOnAction(e -> {
            UnitOfMeasureDto u = unitCombo.getValue();
            if (u == null || (ingredientCombo.getValue() == null && subRecipeCombo.getValue() == null)) {
                banner.showError("Select ingredient or sub-recipe and unit");
                return;
            }
            InventoryItemDto inv = ingredientCombo.getValue();
            RecipeDto sub = subRecipeCombo.getValue();
            pendingLines.add(new SaveRecipeRequest.IngredientLineInput(
                    inv != null ? inv.id() : null,
                    inv == null && sub != null ? sub.id() : null,
                    u.id(),
                    new BigDecimal(qtyField.getText().trim()),
                    new BigDecimal(wastePctField.getText().trim()),
                    false, null, null, pendingLines.size() + 1
            ));
            draftLinesList.getItems().add((inv != null ? inv.name() : sub.recipeName())
                    + " : " + qtyField.getText().trim() + " " + u.code());
        });

        PrimaryButton savePublishBtn = new PrimaryButton("Save & Publish Version");
        savePublishBtn.setOnAction(e -> {
            if (pendingLines.isEmpty()) {
                banner.showError("Add at least one ingredient line");
                return;
            }
            SaveRecipeRequest req = new SaveRecipeRequest(
                    productCombo.getValue() != null ? productCombo.getValue().id() : null,
                    null, false, recipeNameField.getText().trim(), subRecipeCheck.isSelected(),
                    new BigDecimal(yieldField.getText().trim()),
                    unitCombo.getValue() != null ? unitCombo.getValue().id() : null,
                    new BigDecimal(lossField.getText().trim()), prepNotes.getText(), 0, true, true,
                    List.copyOf(pendingLines), List.copyOf(pendingModMappings)
            );
            RecipeDto existing = recipesList.getSelectionModel().getSelectedItem();
            Task<RecipeDto> task = new Task<>() {
                @Override
                protected RecipeDto call() throws Exception {
                    if (existing != null && existing.recipeName().equalsIgnoreCase(recipeNameField.getText().trim())) {
                        return serverClient.addRecipeVersion(sessionContext.accessToken(), existing.id(), req).data();
                    }
                    return serverClient.createRecipe(sessionContext.accessToken(), req).data();
                }
            };
            task.setOnSucceeded(ev -> {
                banner.showSuccess("Published recipe version");
                pendingLines.clear();
                draftLinesList.getItems().clear();
                loadData();
            });
            task.setOnFailed(ev -> handleFailure(task.getException()));
            ioExecutor.submit(task);
        });

        VBox editorPane = new VBox(8,
                new HBox(8, productCombo, recipeNameField, yieldField, lossField, subRecipeCheck),
                prepNotes,
                new HBox(8, ingredientCombo, subRecipeCombo, qtyField, unitCombo, wastePctField, addIngBtn),
                draftLinesList,
                savePublishBtn
        );
        editorPane.getStyleClass().add("pos-card");
        editorPane.setPadding(new Insets(10));

        VBox rightPane = new VBox(8, costSummaryLabel, costBreakdownList, editorPane);
        HBox.setHgrow(rightPane, Priority.ALWAYS);
        HBox split = new HBox(12, recipesList, rightPane);
        VBox.setVgrow(split, Priority.ALWAYS);

        getChildren().addAll(header, banner, split);
        loadData();
    }

    public void loadData() {
        String token = sessionContext.accessToken();
        if (token == null) return;
        Task<RecipeSnap> task = new Task<>() {
            @Override
            protected RecipeSnap call() throws Exception {
                return new RecipeSnap(
                        serverClient.listRecipes(token).data(),
                        serverClient.listInventoryItems(token).data(),
                        serverClient.listInventoryUnits(token).data(),
                        serverClient.searchMenuProducts(token, null, "", 0, 200).data()
                );
            }
        };
        task.setOnSucceeded(e -> {
            RecipeSnap s = task.getValue();
            recipesList.getItems().setAll(s.recipes());
            subRecipeCombo.getItems().setAll(s.recipes().stream().filter(RecipeDto::subRecipe).toList());
            ingredientCombo.getItems().setAll(s.items());
            unitCombo.getItems().setAll(s.units());
            productCombo.getItems().setAll(s.products());
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void loadCostPreview(RecipeDto recipe) {
        if (!sessionContext.hasPermission(PermissionCode.INVENTORY_COST_VIEW)) {
            costSummaryLabel.setText(recipe.recipeName() + " (v" + recipe.activeVersionNumber() + ") — Cost hidden");
            costBreakdownList.getItems().clear();
            return;
        }
        Task<RecipeCostPreviewDto> task = new Task<>() {
            @Override
            protected RecipeCostPreviewDto call() throws Exception {
                return serverClient.previewRecipeCost(sessionContext.accessToken(), recipe.id(), recipe.activeVersionId()).data();
            }
        };
        task.setOnSucceeded(e -> {
            RecipeCostPreviewDto c = task.getValue();
            costSummaryLabel.setText(String.format("%s v%d | Total Cost: $%s | Unit Cost: $%s | Food Cost: %s%%",
                    c.recipeName(), c.versionNumber(), c.totalIngredientCost(), c.unitCostPerYield(), c.estimatedFoodCostPct()));
            costBreakdownList.getItems().setAll(c.lines().stream()
                    .map(l -> String.format("%s: %s %s @ $%s = $%s",
                            l.ingredientName(), l.grossQuantityWithLoss(), l.unitCode(), l.unitCost(), l.lineCost()))
                    .toList());
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void handleFailure(Throwable ex) {
        if (ex instanceof ApiClientException ace && ace.statusCode() == 401) {
            onSessionExpired.run();
            return;
        }
        banner.showError(ex != null ? ex.getMessage() : "Recipe operation failed");
    }

    private record RecipeSnap(
            List<RecipeDto> recipes,
            List<InventoryItemDto> items,
            List<UnitOfMeasureDto> units,
            List<ProductDto> products
    ) {
    }
}
