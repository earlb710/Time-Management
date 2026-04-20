package com.timemanagement.desktop.gui;

import java.util.List;

public class DesktopOAuthClientConfig {
    private static final List<String> DEFAULT_SCOPES = List.of(
            "openid",
            "email",
            "profile",
            "https://www.googleapis.com/auth/drive.file"
    );

    private final String clientId;
    private final List<String> scopes;

    public DesktopOAuthClientConfig(String clientId, List<String> scopes) {
        this.clientId = requireValue(clientId, "A Google OAuth client id is required.");
        this.scopes = scopes == null || scopes.isEmpty() ? DEFAULT_SCOPES : List.copyOf(scopes);
    }

    public static DesktopOAuthClientConfig loadFromEnvironment() {
        String clientId = firstPresent(
                System.getProperty("time.management.googleClientId"),
                System.getenv("TIME_MANAGEMENT_GOOGLE_CLIENT_ID")
        );
        return new DesktopOAuthClientConfig(clientId, DEFAULT_SCOPES);
    }

    public String getClientId() {
        return clientId;
    }

    public List<String> getScopes() {
        return scopes;
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String requireValue(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
