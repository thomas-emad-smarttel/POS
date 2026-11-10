package com.restaurantpos.desktop;

import com.restaurantpos.desktop.infra.LocalCacheDatabase;
import com.restaurantpos.desktop.infra.LocalServerClient;
import com.restaurantpos.desktop.infra.PosExecutors;
import com.restaurantpos.desktop.infra.SessionContext;
import com.restaurantpos.desktop.ui.LoginView;
import com.restaurantpos.desktop.ui.MainShellView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * JavaFX Desktop Application entry point. Starts on {@link LoginView} and transitions to {@link MainShellView}.
 */
public class PosDesktopApplication extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(PosDesktopApplication.class);
    private static final double DEFAULT_WIDTH = 1366;
    private static final double DEFAULT_HEIGHT = 768;

    private ExecutorService ioExecutor;
    private LocalServerClient serverClient;
    private LocalCacheDatabase localCache;
    private String serverUrl;
    private SessionContext sessionContext;
    private Scene mainScene;

    @Override
    public void start(Stage primaryStage) {
        long startNanos = System.nanoTime();
        this.ioExecutor = PosExecutors.newIoExecutor();
        this.sessionContext = new SessionContext();

        this.localCache = initializeLocalCache();

        this.serverUrl = System.getProperty("pos.server.url", "http://localhost:8080");
        this.serverClient = new LocalServerClient(serverUrl);

        this.mainScene = new Scene(createLoginView(), DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.mainScene.getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/styles/tokens.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/styles/pos-theme.css")).toExternalForm()
        );

        primaryStage.setTitle("Restaurant POS — Terminal");
        primaryStage.setMinWidth(1024);
        primaryStage.setMinHeight(680);
        primaryStage.setScene(mainScene);
        primaryStage.show();

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        LOG.info("POS Desktop UI initialized in {} ms (target server={})", elapsedMs, serverUrl);
    }

    private LoginView createLoginView() {
        return new LoginView(serverClient, sessionContext, ioExecutor, this::showAuthenticatedShell);
    }

    private void showAuthenticatedShell() {
        MainShellView shell = new MainShellView(
                serverClient, localCache, serverUrl, sessionContext, ioExecutor, this::handleLogout
        );
        mainScene.setRoot(shell);
    }

    private void handleLogout() {
        sessionContext.clear();
        mainScene.setRoot(createLoginView());
    }

    private LocalCacheDatabase initializeLocalCache() {
        Path dbPath = Path.of(System.getProperty("user.home"), ".restaurant-pos", "terminal-cache.db");
        try {
            if (dbPath.getParent() != null) {
                java.nio.file.Files.createDirectories(dbPath.getParent());
            }
            LocalCacheDatabase cache = new LocalCacheDatabase(dbPath);
            cache.initialize();
            cache.putMetadata("schema_phase", "phase-4-core-pos");
            LOG.info("Terminal SQLite cache ready at {}", dbPath);
            return cache;
        } catch (Exception ex) {
            LOG.warn("Unable to initialize terminal SQLite cache at {}", dbPath, ex);
            return null;
        }
    }

    @Override
    public void stop() {
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }
    }
}
