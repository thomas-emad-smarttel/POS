package com.restaurantpos.desktop.ui;

import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.component.NotificationBanner;
import com.restaurantpos.desktop.ui.component.PrimaryButton;
import com.restaurantpos.desktop.ui.component.SecondaryButton;
import com.restaurantpos.desktop.ui.component.StatusBadge;
import com.restaurantpos.shared.dto.ApiResponse;
import com.restaurantpos.shared.dto.ServerStatusDto;
import com.restaurantpos.shared.dto.security.LoginRequest;
import com.restaurantpos.shared.dto.security.LoginResponse;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Programmatic terminal sign-in screen authenticating against the Local Server API.
 */
public final class LoginView extends StackPane {

    private final LocalServerClient serverClient;
    private final SessionContext sessionContext;
    private final ExecutorService ioExecutor;
    private final Runnable onLoginSuccess;

    private final TextField tenantField;
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final NotificationBanner banner;
    private final StatusBadge serverBadge;
    private final PrimaryButton signInButton;

    public LoginView(
            LocalServerClient serverClient,
            SessionContext sessionContext,
            ExecutorService ioExecutor,
            Runnable onLoginSuccess
    ) {
        this.serverClient = Objects.requireNonNull(serverClient);
        this.sessionContext = Objects.requireNonNull(sessionContext);
        this.ioExecutor = Objects.requireNonNull(ioExecutor);
        this.onLoginSuccess = Objects.requireNonNull(onLoginSuccess);

        this.tenantField = new TextField();
        this.tenantField.setPromptText("Restaurant Tenant Code (e.g. DEMO_GROUP)");
        this.tenantField.getStyleClass().add("pos-input");

        this.usernameField = new TextField();
        this.usernameField.setPromptText("Username");
        this.usernameField.getStyleClass().add("pos-input");

        this.passwordField = new PasswordField();
        this.passwordField.setPromptText("Password");
        this.passwordField.getStyleClass().add("pos-input");
        this.passwordField.setOnAction(e -> attemptLogin());

        this.banner = new NotificationBanner();
        this.serverBadge = new StatusBadge("Checking Server...", StatusBadge.Tone.NEUTRAL);
        this.signInButton = new PrimaryButton("Sign In to Terminal");
        this.signInButton.setMaxWidth(Double.MAX_VALUE);
        this.signInButton.setOnAction(e -> attemptLogin());

        SecondaryButton retryHealthBtn = new SecondaryButton("Ping Local Server");
        retryHealthBtn.setOnAction(e -> checkServerHealth());

        Label heading = new Label("Restaurant POS Terminal");
        heading.getStyleClass().add("pos-heading-lg");
        Label subtitle = new Label("Sign in with your employee credentials to start a shift.");
        subtitle.getStyleClass().add("pos-text-muted");
        subtitle.setWrapText(true);

        HBox statusRow = new HBox(10, serverBadge, retryHealthBtn);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(
                14,
                heading,
                subtitle,
                statusRow,
                banner,
                labeledField("Tenant Code", tenantField),
                labeledField("Username", usernameField),
                labeledField("Password", passwordField),
                signInButton
        );
        card.getStyleClass().add("pos-card");
        card.setMaxWidth(440);
        card.setMaxHeight(USE_PREF_SIZE);

        setPadding(new Insets(32));
        setAlignment(Pos.CENTER);
        getChildren().add(card);

        checkServerHealth();
    }

    private VBox labeledField(String labelText, javafx.scene.Node field) {
        Label label = new Label(labelText);
        label.getStyleClass().add("pos-text-muted");
        return new VBox(4, label, field);
    }

    private void checkServerHealth() {
        serverBadge.setText("Checking Server...");
        serverBadge.setTone(StatusBadge.Tone.NEUTRAL);
        Task<ApiResponse<ServerStatusDto>> task = new Task<>() {
            @Override
            protected ApiResponse<ServerStatusDto> call() throws Exception {
                return serverClient.fetchHealth();
            }
        };
        task.setOnSucceeded(e -> {
            serverBadge.setText("Local Server Online");
            serverBadge.setTone(StatusBadge.Tone.SUCCESS);
        });
        task.setOnFailed(e -> {
            serverBadge.setText("Local Server Offline");
            serverBadge.setTone(StatusBadge.Tone.DANGER);
        });
        ioExecutor.submit(task);
    }

    private void attemptLogin() {
        banner.clear();
        String tenantCode = tenantField.getText() != null ? tenantField.getText().trim() : "";
        String username = usernameField.getText() != null ? usernameField.getText().trim() : "";
        String password = passwordField.getText() != null ? passwordField.getText() : "";
        if (tenantCode.isEmpty() || username.isEmpty() || password.isEmpty()) {
            banner.showError("Tenant code, username, and password are required.");
            return;
        }

        signInButton.setDisable(true);
        signInButton.setText("Signing In...");

        LoginRequest request = new LoginRequest(tenantCode, username, password, null, resolveConfiguredTerminalId());
        Task<ApiResponse<LoginResponse>> task = new Task<>() {
            @Override
            protected ApiResponse<LoginResponse> call() throws Exception {
                return serverClient.login(request);
            }
        };

        task.setOnSucceeded(e -> {
            signInButton.setDisable(false);
            signInButton.setText("Sign In to Terminal");
            ApiResponse<LoginResponse> resp = task.getValue();
            if (resp != null && resp.success() && resp.data() != null) {
                passwordField.clear();
                sessionContext.setSession(resp.data().accessToken(), resp.data().profile());
                onLoginSuccess.run();
            } else {
                banner.showError("Unexpected response from Local Server.");
            }
        });

        task.setOnFailed(e -> {
            signInButton.setDisable(false);
            signInButton.setText("Sign In to Terminal");
            Throwable ex = task.getException();
            banner.showError(ex != null ? ex.getMessage() : "Unable to authenticate.");
        });

        ioExecutor.submit(task);
    }

    private static UUID resolveConfiguredTerminalId() {
        String raw = System.getProperty("pos.terminal.id");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
