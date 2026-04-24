package com.timemanagement.desktop.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopOAuthClientConfigTest {
    @Test
    void reportsHelpfulGoogleClientIdMessage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DesktopOAuthClientConfig.loadGoogle(" "));

        assertTrue(error.getMessage().contains("Google OAuth client id"));
        assertTrue(error.getMessage().contains("TIME_MANAGEMENT_GOOGLE_CLIENT_ID"));
    }

    @Test
    void acceptsClientIdEnteredOnSetupPage() {
        DesktopOAuthClientConfig config = DesktopOAuthClientConfig.loadMicrosoft("desktop-client-id");

        assertEquals("desktop-client-id", config.getClientId());
    }

    @Test
    void usesLoginScopesForGoogleWebLogin() {
        DesktopOAuthClientConfig config = DesktopOAuthClientConfig.loadGoogleLogin("web-client-id");

        assertEquals("web-client-id", config.getClientId());
        assertEquals(DesktopOAuthClientConfig.googleLoginScopes(), config.getScopes());
    }

    @Test
    void loadsClientIdAndSecretFromInstalledAppSecretsFile(@TempDir Path tempDir) throws IOException {
        Path secretsFile = tempDir.resolve("client_secret_abc.json");
        Files.writeString(secretsFile, """
                {
                  "installed": {
                    "client_id": "123-abc.apps.googleusercontent.com",
                    "client_secret": "GOCSPX-secret",
                    "project_id": "my-project",
                    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                    "token_uri": "https://oauth2.googleapis.com/token",
                    "redirect_uris": ["http://localhost"]
                  }
                }
                """);

        DesktopOAuthClientConfig config = DesktopOAuthClientConfig.fromGoogleClientSecretsFile(
                secretsFile, List.of("openid", "email"));

        assertEquals("123-abc.apps.googleusercontent.com", config.getClientId());
        assertEquals("GOCSPX-secret", config.getClientSecret());
        assertEquals(List.of("openid", "email"), config.getScopes());
    }

    @Test
    void loadsClientIdAndSecretFromWebAppSecretsFile(@TempDir Path tempDir) throws IOException {
        Path secretsFile = tempDir.resolve("client_secret_web.json");
        Files.writeString(secretsFile, """
                {
                  "web": {
                    "client_id": "web-client-id.apps.googleusercontent.com",
                    "client_secret": "web-secret",
                    "project_id": "my-project",
                    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                    "token_uri": "https://oauth2.googleapis.com/token"
                  }
                }
                """);

        DesktopOAuthClientConfig config = DesktopOAuthClientConfig.fromGoogleClientSecretsFile(
                secretsFile, List.of("openid"));

        assertEquals("web-client-id.apps.googleusercontent.com", config.getClientId());
        assertEquals("web-secret", config.getClientSecret());
    }

    @Test
    void clientSecretIsNullWhenNotPresentInSecretsFile(@TempDir Path tempDir) throws IOException {
        Path secretsFile = tempDir.resolve("client_secret_nopwd.json");
        Files.writeString(secretsFile, """
                {
                  "installed": {
                    "client_id": "no-secret-client",
                    "redirect_uris": ["http://localhost"]
                  }
                }
                """);

        DesktopOAuthClientConfig config = DesktopOAuthClientConfig.fromGoogleClientSecretsFile(
                secretsFile, List.of("openid"));

        assertEquals("no-secret-client", config.getClientId());
        assertNull(config.getClientSecret());
    }

    @Test
    void throwsWhenSecretsFileHasNoClientId(@TempDir Path tempDir) throws IOException {
        Path secretsFile = tempDir.resolve("client_secret_bad.json");
        Files.writeString(secretsFile, """
                { "installed": { "client_secret": "s" } }
                """);

        assertThrows(IllegalArgumentException.class,
                () -> DesktopOAuthClientConfig.fromGoogleClientSecretsFile(secretsFile, List.of("openid")));
    }
}
