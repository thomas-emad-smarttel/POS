package com.restaurantpos.desktop.ui.component;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * Hosts a screen's content alongside standardized loading, empty, error, offline, and access-denied states.
 */
public final class ViewStateContainer extends StackPane {

    private final Node contentNode;
    private final LoadingIndicator loadingView;
    private final EmptyState emptyView;
    private final EmptyState noResultsView;
    private final EmptyState permissionDeniedView;
    private final ErrorState errorView;
    private final ErrorState offlineView;

    public ViewStateContainer(Node contentNode, Runnable retryAction) {
        this.contentNode = Objects.requireNonNull(contentNode, "contentNode must not be null");
        this.loadingView = new LoadingIndicator("Loading...");
        this.emptyView = new EmptyState("No Records Yet", "Nothing to display in this view.");
        this.noResultsView = new EmptyState("No Matching Results", "Try clearing filters or search keywords.");
        this.permissionDeniedView = new EmptyState("Access Restricted", "Your role does not allow viewing this section.");
        this.errorView = new ErrorState("Something Went Wrong", "Unable to load data from Local Server.", retryAction);
        this.offlineView = new ErrorState("Local Server Offline", "Terminal cannot reach the branch Local Server.", retryAction);

        getChildren().addAll(
                this.contentNode,
                this.loadingView,
                this.emptyView,
                this.noResultsView,
                this.permissionDeniedView,
                this.errorView,
                this.offlineView
        );
        setState(ViewState.CONTENT);
    }

    public void setState(ViewState state) {
        setVisibleAndManaged(contentNode, state == ViewState.CONTENT);
        setVisibleAndManaged(loadingView, state == ViewState.LOADING);
        setVisibleAndManaged(emptyView, state == ViewState.EMPTY);
        setVisibleAndManaged(noResultsView, state == ViewState.NO_RESULTS);
        setVisibleAndManaged(permissionDeniedView, state == ViewState.PERMISSION_DENIED);
        setVisibleAndManaged(errorView, state == ViewState.ERROR);
        setVisibleAndManaged(offlineView, state == ViewState.OFFLINE);
    }

    public void showError(String message) {
        errorView.update("Request Failed", message);
        setState(ViewState.ERROR);
    }

    public void showOffline(String message) {
        offlineView.update("Local Server Unreachable", message);
        setState(ViewState.OFFLINE);
    }

    private static void setVisibleAndManaged(Node node, boolean active) {
        node.setVisible(active);
        node.setManaged(active);
    }
}
