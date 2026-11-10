package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.ApiClientException;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.ConfirmDialog;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.StatusBadge;
import com.restaurantpos.desktop.ui.component.ViewState;
import com.restaurantpos.desktop.ui.component.ViewStateContainer;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.security.CreateRoleRequest;
import com.restaurantpos.shared.dto.security.PermissionDto;
import com.restaurantpos.shared.dto.security.RoleDto;
import com.restaurantpos.shared.dto.security.UpdateRolePermissionsRequest;
import com.restaurantpos.shared.dto.security.UpdateRoleRequest;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Role management and module-grouped permission matrix editor with sensitive permission warnings.
 */
public final class RolePermissionMatrixView extends VBox {

    private record RoleMatrixData(List<RoleDto> roles, List<PermissionDto> permissions) {
    }

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final ListView<RoleDto> roleListView = new ListView<>();
    private final Label selectedRoleLabel = new Label("Select a role");
    private final StatusBadge templateBadge = new StatusBadge("CUSTOM", StatusBadge.Tone.NEUTRAL);
    private final StatusBadge activeBadge = new StatusBadge("ACTIVE", StatusBadge.Tone.SUCCESS);
    private final TextField editRoleNameField = new TextField();
    private final TextField editDiscountPctField = new TextField();
    private final CheckBox editActiveBox = new CheckBox("Role Active");
    private final VBox matrixContainer = new VBox(12);

    private final TextField newRoleCodeField = new TextField();
    private final TextField newRoleNameField = new TextField();
    private final ComboBox<String> newRoleScopeCombo = new ComboBox<>();

    private final Map<String, CheckBox> permissionCheckboxes = new HashMap<>();
    private final Map<String, PermissionDto> permissionsByCode = new HashMap<>();
    private final ViewStateContainer stateContainer;
    private RoleDto selectedRole;

    public RolePermissionMatrixView(
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

        configureRoleList();
        HBox split = new HBox(16, buildLeftPane(), buildRightPane());
        VBox.setVgrow(split, Priority.ALWAYS);

        this.stateContainer = new ViewStateContainer(split, this::loadData);
        VBox.setVgrow(stateContainer, Priority.ALWAYS);

        Label title = new Label("Roles & Permission Matrix");
        title.getStyleClass().add("pos-heading-lg");
        SecondaryButton refreshBtn = new SecondaryButton("Refresh");
        refreshBtn.setOnAction(e -> loadData());
        HBox header = new HBox(12, title, refreshBtn);

        getChildren().addAll(header, banner, stateContainer);
        loadData();
    }

    private void configureRoleList() {
        roleListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(RoleDto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String tag = item.systemTemplate() ? " [System]" : "";
                    String state = item.active() ? "" : " (Inactive)";
                    setText(item.name() + tag + state + " — " + item.permissionCodes().size() + " perms");
                }
            }
        });
        roleListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectRole(newVal);
            }
        });
    }

    private VBox buildLeftPane() {
        newRoleCodeField.setPromptText("CODE (e.g. SHIFT_LEAD)");
        newRoleCodeField.getStyleClass().add("pos-input");
        newRoleNameField.setPromptText("Role Display Name");
        newRoleNameField.getStyleClass().add("pos-input");
        newRoleScopeCombo.getItems().setAll("BRANCH", "TENANT");
        newRoleScopeCombo.setValue("BRANCH");

        PrimaryButton createBtn = new PrimaryButton("Create Role");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setOnAction(e -> createRole());

        VBox createBox = new VBox(8, new Label("Create Custom Role"), newRoleCodeField, newRoleNameField, newRoleScopeCombo, createBtn);
        createBox.getStyleClass().add("pos-module-card");

        VBox.setVgrow(roleListView, Priority.ALWAYS);
        VBox left = new VBox(12, new Label("Roles"), roleListView, createBox);
        left.getStyleClass().add("pos-card");
        left.setPrefWidth(320);
        return left;
    }

    private VBox buildRightPane() {
        selectedRoleLabel.getStyleClass().add("pos-heading-md");
        editRoleNameField.setPromptText("Role Name");
        editRoleNameField.getStyleClass().add("pos-input");
        editDiscountPctField.setPromptText("Max Discount %");
        editDiscountPctField.getStyleClass().add("pos-input");
        editDiscountPctField.setPrefWidth(110);

        PrimaryButton saveDetailsBtn = new PrimaryButton("Save Role Details");
        saveDetailsBtn.setOnAction(e -> saveRoleDetails());

        HBox roleHeader = new HBox(10, selectedRoleLabel, templateBadge, activeBadge);
        HBox editBar = new HBox(10, editRoleNameField, editDiscountPctField, editActiveBox, saveDetailsBtn);

        PrimaryButton saveMatrixBtn = new PrimaryButton("Save Permission Matrix");
        saveMatrixBtn.setOnAction(e -> savePermissionMatrix());

        ScrollPane matrixScroll = new ScrollPane(matrixContainer);
        matrixScroll.setFitToWidth(true);
        VBox.setVgrow(matrixScroll, Priority.ALWAYS);

        VBox right = new VBox(12, roleHeader, editBar, matrixScroll, saveMatrixBtn);
        right.getStyleClass().add("pos-card");
        HBox.setHgrow(right, Priority.ALWAYS);
        return right;
    }

    public final void loadData() {
        banner.clear();
        stateContainer.setState(ViewState.LOADING);
        String token = sessionContext.accessToken();

        Task<RoleMatrixData> task = new Task<>() {
            @Override
            protected RoleMatrixData call() throws Exception {
                List<RoleDto> roles = serverClient.listRoles(token).data();
                List<PermissionDto> perms = serverClient.listPermissions(token).data();
                return new RoleMatrixData(roles, perms);
            }
        };

        task.setOnSucceeded(e -> {
            RoleMatrixData data = task.getValue();
            buildPermissionMatrix(data.permissions());
            roleListView.getItems().setAll(data.roles());
            if (!data.roles().isEmpty()) {
                roleListView.getSelectionModel().selectFirst();
                stateContainer.setState(ViewState.CONTENT);
            } else {
                stateContainer.setState(ViewState.EMPTY);
            }
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void buildPermissionMatrix(List<PermissionDto> permissions) {
        permissionCheckboxes.clear();
        permissionsByCode.clear();
        matrixContainer.getChildren().clear();

        Map<String, List<PermissionDto>> byModule = new LinkedHashMap<>();
        for (PermissionDto p : permissions) {
            permissionsByCode.put(p.code(), p);
            byModule.computeIfAbsent(p.module(), k -> new java.util.ArrayList<>()).add(p);
        }

        for (Map.Entry<String, List<PermissionDto>> entry : byModule.entrySet()) {
            String moduleName = entry.getKey();
            List<PermissionDto> modulePerms = entry.getValue();

            Label modTitle = new Label(moduleName + " (" + modulePerms.size() + ")");
            modTitle.getStyleClass().add("pos-heading-md");

            SecondaryButton selectAllBtn = new SecondaryButton("Select All");
            selectAllBtn.setOnAction(e -> modulePerms.forEach(p -> permissionCheckboxes.get(p.code()).setSelected(true)));
            SecondaryButton clearAllBtn = new SecondaryButton("Clear All");
            clearAllBtn.setOnAction(e -> modulePerms.forEach(p -> permissionCheckboxes.get(p.code()).setSelected(false)));

            HBox modHeader = new HBox(10, modTitle, selectAllBtn, clearAllBtn);
            VBox modCard = new VBox(8, modHeader);
            modCard.getStyleClass().add("pos-module-card");

            for (PermissionDto p : modulePerms) {
                CheckBox cb = new CheckBox(p.code() + " — " + p.description());
                cb.setWrapText(true);
                permissionCheckboxes.put(p.code(), cb);

                HBox row = new HBox(8, cb);
                if (p.sensitive()) {
                    row.getChildren().add(new StatusBadge("SENSITIVE", StatusBadge.Tone.WARNING));
                }
                modCard.getChildren().add(row);
            }
            matrixContainer.getChildren().add(modCard);
        }
    }

    private void selectRole(RoleDto role) {
        this.selectedRole = role;
        selectedRoleLabel.setText(role.name() + " (" + role.code() + ") — Scope: " + role.scopeType());
        templateBadge.setText(role.systemTemplate() ? "SYSTEM TEMPLATE" : "CUSTOM");
        templateBadge.setTone(role.systemTemplate() ? StatusBadge.Tone.WARNING : StatusBadge.Tone.NEUTRAL);
        activeBadge.setText(role.active() ? "ACTIVE" : "INACTIVE");
        activeBadge.setTone(role.active() ? StatusBadge.Tone.SUCCESS : StatusBadge.Tone.DANGER);

        editRoleNameField.setText(role.name());
        editDiscountPctField.setText(role.maxDiscountPct().toPlainString());
        editActiveBox.setSelected(role.active());

        permissionCheckboxes.forEach((code, cb) -> cb.setSelected(role.permissionCodes().contains(code)));
    }

    private void createRole() {
        banner.clear();
        String code = newRoleCodeField.getText().trim().toUpperCase();
        String name = newRoleNameField.getText().trim();
        if (code.isEmpty() || name.isEmpty()) {
            banner.showError("Role code and display name are required.");
            return;
        }
        String token = sessionContext.accessToken();
        CreateRoleRequest req = new CreateRoleRequest(code, name, null, newRoleScopeCombo.getValue(), BigDecimal.ZERO, Set.of());
        Task<ApiResponse<RoleDto>> task = new Task<>() {
            @Override
            protected ApiResponse<RoleDto> call() throws Exception {
                return serverClient.createRole(token, req);
            }
        };
        task.setOnSucceeded(e -> {
            newRoleCodeField.clear();
            newRoleNameField.clear();
            banner.showSuccess("Created role " + task.getValue().data().code());
            loadData();
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void saveRoleDetails() {
        if (selectedRole == null) {
            return;
        }
        banner.clear();
        BigDecimal maxDiscount;
        try {
            maxDiscount = new BigDecimal(editDiscountPctField.getText().trim());
        } catch (NumberFormatException ex) {
            banner.showError("Enter a valid numeric discount percentage.");
            return;
        }
        String token = sessionContext.accessToken();
        UUID roleId = selectedRole.id();
        UpdateRoleRequest req = new UpdateRoleRequest(
                editRoleNameField.getText().trim(),
                selectedRole.description(),
                selectedRole.scopeType(),
                maxDiscount,
                editActiveBox.isSelected()
        );
        Task<ApiResponse<RoleDto>> task = new Task<>() {
            @Override
            protected ApiResponse<RoleDto> call() throws Exception {
                return serverClient.updateRole(token, roleId, req);
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Saved role details for " + selectedRole.code());
            loadData();
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void savePermissionMatrix() {
        if (selectedRole == null) {
            return;
        }
        banner.clear();
        Set<String> selectedCodes = new HashSet<>();
        permissionCheckboxes.forEach((code, cb) -> {
            if (cb.isSelected()) {
                selectedCodes.add(code);
            }
        });

        List<String> removedSensitive = selectedRole.permissionCodes().stream()
                .filter(code -> !selectedCodes.contains(code))
                .filter(code -> {
                    PermissionDto dto = permissionsByCode.get(code);
                    return dto != null && dto.sensitive();
                })
                .sorted()
                .toList();

        if (!removedSensitive.isEmpty() && !ConfirmDialog.confirm(
                "Remove Sensitive Permissions",
                "Remove " + removedSensitive.size() + " sensitive permission(s) from " + selectedRole.name() + "?",
                "Removing sensitive permissions takes effect immediately for active sessions: "
                        + String.join(", ", removedSensitive)
        )) {
            return;
        }

        String token = sessionContext.accessToken();
        UUID roleId = selectedRole.id();
        Task<ApiResponse<RoleDto>> task = new Task<>() {
            @Override
            protected ApiResponse<RoleDto> call() throws Exception {
                return serverClient.updateRolePermissions(token, roleId, new UpdateRolePermissionsRequest(selectedCodes));
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Updated permissions for role " + selectedRole.code());
            loadData();
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void handleFailure(Throwable ex) {
        if (ex instanceof ApiClientException ace) {
            if (ace.statusCode() == 401) {
                onSessionExpired.run();
                return;
            }
            if (ace.statusCode() == 403 && selectedRole == null) {
                stateContainer.setState(ViewState.PERMISSION_DENIED);
                return;
            }
        }
        banner.showError(ex != null ? ex.getMessage() : "Operation failed.");
    }
}
