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
import com.restaurantpos.shared.dto.security.AssignUserRolesRequest;
import com.restaurantpos.shared.dto.security.BranchSummaryDto;
import com.restaurantpos.shared.dto.security.CreateUserRequest;
import com.restaurantpos.shared.dto.security.RoleDto;
import com.restaurantpos.shared.dto.security.UserRoleAssignmentDto;
import com.restaurantpos.shared.dto.security.UserSummaryDto;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Employee account management, branch access assignment, and scoped user-role assignment screen.
 */
public final class UserManagementView extends VBox {

    private record UserDataBundle(
            List<UserSummaryDto> users,
            List<BranchSummaryDto> branches,
            List<RoleDto> roles
    ) {
    }

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final ListView<UserSummaryDto> userListView = new ListView<>();
    private final Label selectedUserHeader = new Label("Select a user to edit access & roles");
    private final StatusBadge statusBadge = new StatusBadge("NONE", StatusBadge.Tone.NEUTRAL);
    private final SecondaryButton toggleActiveBtn = new SecondaryButton("Toggle Active");

    private final VBox branchCheckboxesBox = new VBox(6);
    private final VBox roleAssignmentsBox = new VBox(8);
    private final Map<UUID, CheckBox> branchCheckboxes = new HashMap<>();
    private final Map<UUID, CheckBox> roleCheckboxes = new HashMap<>();
    private final Map<UUID, ComboBox<UUID>> roleBranchSelectors = new HashMap<>();

    private final TextField newUsernameField = new TextField();
    private final TextField newFullNameField = new TextField();
    private final TextField newEmailField = new TextField();
    private final PasswordField newPasswordField = new PasswordField();

    private final ViewStateContainer stateContainer;
    private List<BranchSummaryDto> branches = List.of();
    private List<RoleDto> roles = List.of();
    private UserSummaryDto selectedUser;

    public UserManagementView(
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

        configureUserList();
        HBox split = new HBox(16, buildLeftPane(), buildRightPane());
        VBox.setVgrow(split, Priority.ALWAYS);

        this.stateContainer = new ViewStateContainer(split, this::loadData);
        VBox.setVgrow(stateContainer, Priority.ALWAYS);

        Label title = new Label("Users, Branch Access & Role Assignments");
        title.getStyleClass().add("pos-heading-lg");
        SecondaryButton refreshBtn = new SecondaryButton("Refresh");
        refreshBtn.setOnAction(e -> loadData());
        HBox header = new HBox(12, title, refreshBtn);

        getChildren().addAll(header, banner, stateContainer);
        loadData();
    }

    private void configureUserList() {
        userListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(UserSummaryDto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.fullName() + " (@" + item.username() + ") — " + (item.active() ? "Active" : "Disabled"));
                }
            }
        });
        userListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectUser(newVal);
            }
        });
    }

    private VBox buildLeftPane() {
        newUsernameField.setPromptText("Username");
        newUsernameField.getStyleClass().add("pos-input");
        newFullNameField.setPromptText("Full Name");
        newFullNameField.getStyleClass().add("pos-input");
        newEmailField.setPromptText("Email (optional)");
        newEmailField.getStyleClass().add("pos-input");
        newPasswordField.setPromptText("Initial Password (min 8 chars)");
        newPasswordField.getStyleClass().add("pos-input");

        PrimaryButton createBtn = new PrimaryButton("Create Employee");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setOnAction(e -> createUser());

        VBox createBox = new VBox(8, new Label("Create Employee"), newUsernameField, newFullNameField, newEmailField, newPasswordField, createBtn);
        createBox.getStyleClass().add("pos-module-card");

        VBox.setVgrow(userListView, Priority.ALWAYS);
        VBox left = new VBox(12, new Label("Employees"), userListView, createBox);
        left.getStyleClass().add("pos-card");
        left.setPrefWidth(340);
        return left;
    }

    private ScrollPane buildRightPane() {
        selectedUserHeader.getStyleClass().add("pos-heading-md");
        toggleActiveBtn.setOnAction(e -> toggleSelectedUserActive());
        toggleActiveBtn.setDisable(true);

        HBox topRow = new HBox(10, selectedUserHeader, statusBadge, toggleActiveBtn);

        PrimaryButton saveBranchesBtn = new PrimaryButton("Save Branch Access");
        saveBranchesBtn.setOnAction(e -> saveBranches());
        VBox branchBox = new VBox(8, new Label("Authorized Branches"), branchCheckboxesBox, saveBranchesBtn);
        branchBox.getStyleClass().add("pos-module-card");

        PrimaryButton saveRolesBtn = new PrimaryButton("Save Role Assignments");
        saveRolesBtn.setOnAction(e -> saveRoles());
        VBox roleBox = new VBox(8, new Label("Role Assignments (Tenant-Wide or Branch-Scoped)"), roleAssignmentsBox, saveRolesBtn);
        roleBox.getStyleClass().add("pos-module-card");

        VBox container = new VBox(14, topRow, branchBox, roleBox);
        container.getStyleClass().add("pos-card");

        ScrollPane scroll = new ScrollPane(container);
        scroll.setFitToWidth(true);
        HBox.setHgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    public final void loadData() {
        banner.clear();
        stateContainer.setState(ViewState.LOADING);
        String token = sessionContext.accessToken();

        Task<UserDataBundle> task = new Task<>() {
            @Override
            protected UserDataBundle call() throws Exception {
                List<UserSummaryDto> users = serverClient.listUsers(token, 0, 100).data();
                List<BranchSummaryDto> branchList = serverClient.listBranches(token).data();
                List<RoleDto> roleList = serverClient.listRoles(token).data();
                return new UserDataBundle(users, branchList, roleList);
            }
        };

        task.setOnSucceeded(e -> {
            UserDataBundle bundle = task.getValue();
            this.branches = bundle.branches();
            this.roles = bundle.roles();
            userListView.getItems().setAll(bundle.users());
            rebuildBranchAndRoleControls();
            if (!bundle.users().isEmpty()) {
                userListView.getSelectionModel().selectFirst();
                stateContainer.setState(ViewState.CONTENT);
            } else {
                stateContainer.setState(ViewState.EMPTY);
            }
        });

        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void rebuildBranchAndRoleControls() {
        branchCheckboxes.clear();
        branchCheckboxesBox.getChildren().clear();
        for (BranchSummaryDto branch : branches) {
            CheckBox cb = new CheckBox(branch.name() + " (" + branch.code() + ")");
            branchCheckboxes.put(branch.id(), cb);
            branchCheckboxesBox.getChildren().add(cb);
        }

        roleCheckboxes.clear();
        roleBranchSelectors.clear();
        roleAssignmentsBox.getChildren().clear();
        for (RoleDto role : roles) {
            CheckBox cb = new CheckBox(role.name() + " [" + role.code() + "]");
            ComboBox<UUID> branchCombo = new ComboBox<>();
            branchCombo.getItems().add(null);
            for (BranchSummaryDto b : branches) {
                branchCombo.getItems().add(b.id());
            }
            branchCombo.setPromptText("Tenant-Wide (All Allowed)");
            roleCheckboxes.put(role.id(), cb);
            roleBranchSelectors.put(role.id(), branchCombo);
            HBox row = new HBox(10, cb, new Label("Scope Branch ID:"), branchCombo);
            roleAssignmentsBox.getChildren().add(row);
        }
    }

    private void selectUser(UserSummaryDto user) {
        this.selectedUser = user;
        selectedUserHeader.setText(user.fullName() + " (@" + user.username() + ")");
        statusBadge.setText(user.active() ? "ACTIVE" : "DISABLED");
        statusBadge.setTone(user.active() ? StatusBadge.Tone.SUCCESS : StatusBadge.Tone.DANGER);
        toggleActiveBtn.setDisable(false);
        toggleActiveBtn.setText(user.active() ? "Deactivate User" : "Activate User");

        branchCheckboxes.forEach((branchId, cb) -> cb.setSelected(user.allowedBranchIds().contains(branchId)));

        Map<UUID, UUID> assignedByRole = new HashMap<>();
        for (UserRoleAssignmentDto assignment : user.roleAssignments()) {
            assignedByRole.put(assignment.roleId(), assignment.branchId());
        }
        roleCheckboxes.forEach((roleId, cb) -> {
            boolean assigned = assignedByRole.containsKey(roleId);
            cb.setSelected(assigned);
            ComboBox<UUID> combo = roleBranchSelectors.get(roleId);
            if (combo != null) {
                combo.setValue(assigned ? assignedByRole.get(roleId) : null);
            }
        });
    }

    private void createUser() {
        banner.clear();
        String username = newUsernameField.getText().trim();
        String fullName = newFullNameField.getText().trim();
        String email = newEmailField.getText().trim();
        String password = newPasswordField.getText();
        if (username.length() < 3 || fullName.isEmpty() || password.length() < 8) {
            banner.showError("Provide username (3+ chars), full name, and password (8+ chars).");
            return;
        }
        List<UUID> defaultBranch = sessionContext.profile() != null && sessionContext.profile().activeBranchId() != null
                ? List.of(sessionContext.profile().activeBranchId())
                : List.of();
        String token = sessionContext.accessToken();
        CreateUserRequest req = new CreateUserRequest(username, fullName, email.isEmpty() ? null : email, password, defaultBranch);
        Task<ApiResponse<UserSummaryDto>> task = new Task<>() {
            @Override
            protected ApiResponse<UserSummaryDto> call() throws Exception {
                return serverClient.createUser(token, req);
            }
        };
        task.setOnSucceeded(e -> {
            newUsernameField.clear();
            newFullNameField.clear();
            newEmailField.clear();
            newPasswordField.clear();
            banner.showSuccess("Created user @" + task.getValue().data().username());
            loadData();
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void toggleSelectedUserActive() {
        if (selectedUser == null) {
            return;
        }
        boolean nextActive = !selectedUser.active();
        if (!nextActive && !ConfirmDialog.confirm(
                "Deactivate Employee",
                "Deactivate @" + selectedUser.username() + "?",
                "Deactivating this user immediately revokes all their active POS terminal sessions."
        )) {
            return;
        }
        String token = sessionContext.accessToken();
        UUID userId = selectedUser.id();
        Task<ApiResponse<UserSummaryDto>> task = new Task<>() {
            @Override
            protected ApiResponse<UserSummaryDto> call() throws Exception {
                return serverClient.setUserStatus(token, userId, nextActive);
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Updated user status for @" + task.getValue().data().username());
            loadData();
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void saveBranches() {
        if (selectedUser == null) {
            return;
        }
        List<UUID> chosen = branchCheckboxes.entrySet().stream()
                .filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey)
                .toList();
        String token = sessionContext.accessToken();
        UUID userId = selectedUser.id();
        Task<ApiResponse<UserSummaryDto>> task = new Task<>() {
            @Override
            protected ApiResponse<UserSummaryDto> call() throws Exception {
                return serverClient.assignUserBranches(token, userId, chosen);
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Branch access updated for @" + selectedUser.username());
            loadData();
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void saveRoles() {
        if (selectedUser == null) {
            return;
        }
        List<AssignUserRolesRequest.RoleScopeEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, CheckBox> entry : roleCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                UUID branchId = roleBranchSelectors.get(entry.getKey()).getValue();
                entries.add(new AssignUserRolesRequest.RoleScopeEntry(entry.getKey(), branchId));
            }
        }
        String token = sessionContext.accessToken();
        UUID userId = selectedUser.id();
        Task<ApiResponse<UserSummaryDto>> task = new Task<>() {
            @Override
            protected ApiResponse<UserSummaryDto> call() throws Exception {
                return serverClient.assignUserRoles(token, userId, new AssignUserRolesRequest(entries));
            }
        };
        task.setOnSucceeded(e -> {
            banner.showSuccess("Role assignments saved for @" + selectedUser.username());
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
            if (ace.statusCode() == 403 && selectedUser == null) {
                stateContainer.setState(ViewState.PERMISSION_DENIED);
                return;
            }
        }
        banner.showError(ex != null ? ex.getMessage() : "Operation failed.");
    }
}
