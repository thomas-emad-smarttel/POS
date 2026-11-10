package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.StatusBadge;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.security.ChangePasswordRequest;
import com.restaurantpos.shared.dto.security.UserProfileDto;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Displays the signed-in employee profile, branch switcher, effective permissions, and password change form.
 */
public final class ProfileView extends VBox {

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onSessionUpdated;
    private final Runnable onSessionExpired;

    private final NotificationBanner banner = new NotificationBanner();
    private final Label userSummaryLabel = new Label();
    private final ComboBox<UUID> branchSelector = new ComboBox<>();
    private final ListView<String> rolesList = new ListView<>();
    private final ListView<String> permissionsList = new ListView<>();
    private final PasswordField currentPasswordField = new PasswordField();
    private final PasswordField newPasswordField = new PasswordField();

    public ProfileView(
            LocalServerClient serverClient,
            SessionContext sessionContext,
            ExecutorService ioExecutor,
            Runnable onSessionUpdated,
            Runnable onSessionExpired
    ) {
        this.serverClient = Objects.requireNonNull(serverClient);
        this.sessionContext = Objects.requireNonNull(sessionContext);
        this.ioExecutor = Objects.requireNonNull(ioExecutor);
        this.onSessionUpdated = Objects.requireNonNull(onSessionUpdated);
        this.onSessionExpired = Objects.requireNonNull(onSessionExpired);

        setSpacing(16);
        setPadding(new Insets(4));

        Label title = new Label("My Profile & Effective Access");
        title.getStyleClass().add("pos-heading-lg");

        userSummaryLabel.getStyleClass().add("pos-text-muted");
        userSummaryLabel.setWrapText(true);

        SecondaryButton refreshBtn = new SecondaryButton("Refresh Profile");
        refreshBtn.setOnAction(e -> refreshProfile());

        PrimaryButton switchBranchBtn = new PrimaryButton("Switch Active Branch");
        switchBranchBtn.setOnAction(e -> switchBranch());

        HBox branchBar = new HBox(10, new Label("Active Branch:"), branchSelector, switchBranchBtn, refreshBtn);

        VBox profileCard = new VBox(12, title, banner, userSummaryLabel, branchBar);
        profileCard.getStyleClass().add("pos-card");

        rolesList.setPrefHeight(160);
        permissionsList.setPrefHeight(160);
        VBox rolesBox = new VBox(6, new Label("Assigned Roles"), rolesList);
        VBox permsBox = new VBox(6, new Label("Effective Permissions (Server-Resolved)"), permissionsList);
        HBox.setHgrow(rolesBox, Priority.ALWAYS);
        HBox.setHgrow(permsBox, Priority.ALWAYS);
        HBox listsRow = new HBox(16, rolesBox, permsBox);
        listsRow.getStyleClass().add("pos-card");

        currentPasswordField.setPromptText("Current Password");
        currentPasswordField.getStyleClass().add("pos-input");
        newPasswordField.setPromptText("New Password (min 8 chars)");
        newPasswordField.getStyleClass().add("pos-input");
        PrimaryButton changePwdBtn = new PrimaryButton("Update Password (Revokes Other Sessions)");
        changePwdBtn.setOnAction(e -> changePassword());

        HBox pwdHeader = new HBox(10, new Label("Change Password"), new StatusBadge("Security Sensitive", StatusBadge.Tone.WARNING));
        VBox passwordCard = new VBox(10, pwdHeader, currentPasswordField, newPasswordField, changePwdBtn);
        passwordCard.getStyleClass().add("pos-card");

        getChildren().addAll(profileCard, listsRow, passwordCard);
        renderProfile();
    }

    public void renderProfile() {
        UserProfileDto profile = sessionContext.profile();
        if (profile == null) {
            return;
        }
        userSummaryLabel.setText(String.format(
                "User: %s (%s) | Tenant: %s | Session Expires: %s",
                profile.fullName(),
                profile.username(),
                profile.tenantId(),
                profile.sessionExpiresAt()
        ));
        branchSelector.getItems().setAll(profile.allowedBranchIds());
        branchSelector.setValue(profile.activeBranchId());
        rolesList.getItems().setAll(profile.assignedRoles().stream().sorted().toList());
        permissionsList.getItems().setAll(profile.effectivePermissions().stream().sorted().toList());
    }

    private void refreshProfile() {
        banner.clear();
        String token = sessionContext.accessToken();
        Task<ApiResponse<UserProfileDto>> task = new Task<>() {
            @Override
            protected ApiResponse<UserProfileDto> call() throws Exception {
                return serverClient.fetchProfile(token);
            }
        };
        task.setOnSucceeded(e -> {
            sessionContext.updateProfile(task.getValue().data());
            renderProfile();
            onSessionUpdated.run();
            banner.showSuccess("Profile & effective permissions refreshed from Local Server.");
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void switchBranch() {
        UUID selected = branchSelector.getValue();
        if (selected == null) {
            banner.showError("Select an authorized branch first.");
            return;
        }
        banner.clear();
        String token = sessionContext.accessToken();
        Task<ApiResponse<UserProfileDto>> task = new Task<>() {
            @Override
            protected ApiResponse<UserProfileDto> call() throws Exception {
                return serverClient.switchBranch(token, selected);
            }
        };
        task.setOnSucceeded(e -> {
            sessionContext.updateProfile(task.getValue().data());
            renderProfile();
            onSessionUpdated.run();
            banner.showSuccess("Active branch switched to " + selected);
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void changePassword() {
        banner.clear();
        String cur = currentPasswordField.getText();
        String next = newPasswordField.getText();
        if (cur.isEmpty() || next.length() < 8) {
            banner.showError("Enter current password and a new password of at least 8 characters.");
            return;
        }
        String token = sessionContext.accessToken();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                serverClient.changePassword(token, new ChangePasswordRequest(cur, next));
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            currentPasswordField.clear();
            newPasswordField.clear();
            banner.showSuccess("Password updated. All sessions were revoked — please sign in again.");
            onSessionExpired.run();
        });
        task.setOnFailed(e -> handleFailure(task.getException()));
        ioExecutor.submit(task);
    }

    private void handleFailure(Throwable ex) {
        if (ex instanceof com.restaurantpos.desktop.infra.ApiClientException ace && ace.statusCode() == 401) {
            onSessionExpired.run();
            return;
        }
        banner.showError(ex != null ? ex.getMessage() : "Operation failed.");
    }
}
