package com.restaurantpos.desktop.infra;

import com.restaurantpos.shared.dto.security.UserProfileDto;
import com.restaurantpos.shared.security.PermissionCode;

import java.util.Objects;

/**
 * In-memory authenticated session state for the active JavaFX terminal session.
 * Used exclusively for UX navigation; all security enforcement occurs on the Local Server.
 */
public final class SessionContext {

    private String accessToken;
    private UserProfileDto profile;

    public synchronized void setSession(String accessToken, UserProfileDto profile) {
        this.accessToken = Objects.requireNonNull(accessToken, "accessToken must not be null");
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
    }

    public synchronized void updateProfile(UserProfileDto profile) {
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
    }

    public synchronized void clear() {
        this.accessToken = null;
        this.profile = null;
    }

    public synchronized boolean isAuthenticated() {
        return accessToken != null && profile != null;
    }

    public synchronized String accessToken() {
        return accessToken;
    }

    public synchronized UserProfileDto profile() {
        return profile;
    }

    public synchronized boolean hasPermission(PermissionCode code) {
        return profile != null
                && profile.effectivePermissions() != null
                && profile.effectivePermissions().contains(code.code());
    }

    public synchronized boolean hasAnyPermission(PermissionCode... codes) {
        for (PermissionCode code : codes) {
            if (hasPermission(code)) {
                return true;
            }
        }
        return false;
    }
}
