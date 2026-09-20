package com.aks.waybill.security;

/** Holds the authenticated desktop session for the current JavaFX application. */
public final class SessionContext {
    private static AuthService.UserRecord currentUser;

    private SessionContext() {}

    public static void setCurrentUser(AuthService.UserRecord user) {
        currentUser = user;
    }

    public static AuthService.UserRecord getCurrentUser() {
        return currentUser;
    }

    public static long requireUserId() {
        if (currentUser == null) throw new IllegalStateException("No authenticated user is available.");
        return currentUser.id();
    }

    public static String requireUserCode() {
        if (currentUser == null || currentUser.userCode() == null || currentUser.userCode().isBlank()) {
            throw new IllegalStateException("The logged-in user does not have a waybill user code configured.");
        }
        return currentUser.userCode();
    }

    public static boolean isAdmin() {
        return currentUser != null && "ADMIN".equalsIgnoreCase(currentUser.role());
    }

    public static void clear() {
        currentUser = null;
    }
}
