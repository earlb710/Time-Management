package com.timemanagement.desktop.gui;

import java.util.List;

public class DesktopOAuthClientConfig {
    private static final List<String> GOOGLE_DEFAULT_SCOPES = List.of(
            "openid",
            "email",
            "profile",
            "https://www.googleapis.com/auth/drive.file"
    );
    private static final List<String> MICROSOFT_DEFAULT_SCOPES = List.of(
            "openid",
            "email",
            "profile",
            "offline_access",
            "User.Read",
            "Files.ReadWrite.AppFolder"
    );

    private final String clientId;
    private final List<String> scopes;

    public DesktopOAuthClientConfig(String clientId, List<String> scopes) {
        this.clientId = requireValue(clientId, "An OAuth client id is required.");
        this.scopes = scopes == null || scopes.isEmpty() ? List.of() : List.copyOf(scopes);
    }

    public static DesktopOAuthClientConfig loadGoogleFromEnvironment() {
        return new DesktopOAuthClientConfig(
                firstPresent(
                        System.getProperty("time.management.googleClientId"),
                        System.getenv("TIME_MANAGEMENT_GOOGLE_CLIENT_ID")
                ),
                GOOGLE_DEFAULT_SCOPES
        );
    }

    public static DesktopOAuthClientConfig loadMicrosoftFromEnvironment() {
        return new DesktopOAuthClientConfig(
                firstPresent(
                        System.getProperty("time.management.microsoftClientId"),
                        System.getenv("TIME_MANAGEMENT_MICROSOFT_CLIENT_ID")
                ),
                MICROSOFT_DEFAULT_SCOPES
        );
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
