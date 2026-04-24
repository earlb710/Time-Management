package com.timemanagement.desktop.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class DesktopOAuthClientConfig {
    private static final List<String> GOOGLE_LOGIN_SCOPES = List.of(
            "openid",
            "email",
            "profile"
    );
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
    private final String clientSecret;
    private final List<String> scopes;

    public DesktopOAuthClientConfig(String clientId, List<String> scopes) {
        this(clientId, null, scopes);
    }

    public DesktopOAuthClientConfig(String clientId, String clientSecret, List<String> scopes) {
        this.clientId = requireValue(clientId, "An OAuth client id is required.");
        this.clientSecret = (clientSecret != null && !clientSecret.isBlank()) ? clientSecret.trim() : null;
        this.scopes = scopes == null || scopes.isEmpty() ? List.of() : List.copyOf(scopes);
    }

    public static DesktopOAuthClientConfig loadGoogleFromEnvironment() {
        return loadGoogle(null);
    }

    public static DesktopOAuthClientConfig loadGoogle(String preferredClientId) {
        return new DesktopOAuthClientConfig(
                requireValue(
                        firstPresent(
                                preferredClientId,
                                System.getProperty("time.management.googleClientId"),
                                System.getenv("TIME_MANAGEMENT_GOOGLE_CLIENT_ID")
                        ),
                        "A Google OAuth client id is required. Enter it on the Google Drive page or set TIME_MANAGEMENT_GOOGLE_CLIENT_ID."
                ),
                GOOGLE_DEFAULT_SCOPES
        );
    }

    public static DesktopOAuthClientConfig loadGoogleLogin(String preferredClientId) {
        return new DesktopOAuthClientConfig(
                requireValue(
                        firstPresent(
                                preferredClientId,
                                System.getProperty("time.management.googleClientId"),
                                System.getenv("TIME_MANAGEMENT_GOOGLE_CLIENT_ID")
                        ),
                        "A Google web client id is required. Set TIME_MANAGEMENT_GOOGLE_CLIENT_ID to enable Google sign-in."
                ),
                GOOGLE_LOGIN_SCOPES
        );
    }

    /**
     * Loads a Google login config from the first {@code client_secret_*.json} file found in the
     * working directory, or returns {@code null} if no such file is present.
     */
    public static DesktopOAuthClientConfig loadGoogleLoginFromFileIfPresent() {
        Path secretsFile = findGoogleClientSecretsFile();
        if (secretsFile == null) {
            return null;
        }
        return fromGoogleClientSecretsFile(secretsFile, GOOGLE_LOGIN_SCOPES);
    }

    /**
     * Parses a Google OAuth client-secrets JSON file (downloaded from Google Cloud Console) and
     * builds a config using the embedded {@code client_id} and optional {@code client_secret}.
     */
    public static DesktopOAuthClientConfig fromGoogleClientSecretsFile(Path jsonFile, List<String> scopes) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonFile.toFile());
            JsonNode app = root.path("installed");
            if (app.isMissingNode()) {
                app = root.path("web");
            }
            String clientId = app.path("client_id").asText(null);
            String clientSecret = app.path("client_secret").asText(null);
            if (clientId == null || clientId.isBlank()) {
                throw new IllegalArgumentException(
                        "Could not read a client_id from " + jsonFile.getFileName() + ".");
            }
            return new DesktopOAuthClientConfig(clientId, clientSecret, scopes);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + jsonFile.getFileName() + ".", e);
        }
    }

    /**
     * Returns the first {@code client_secret_*.json} file found in the current working directory,
     * or {@code null} if none is present.
     */
    static Path findGoogleClientSecretsFile() {
        Path workingDir = Path.of(System.getProperty("user.dir", ""));
        try (Stream<Path> entries = Files.list(workingDir)) {
            return entries
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("client_secret_") && name.endsWith(".json");
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    public static DesktopOAuthClientConfig loadMicrosoftFromEnvironment() {
        return loadMicrosoft(null);
    }

    public static DesktopOAuthClientConfig loadMicrosoft(String preferredClientId) {
        return new DesktopOAuthClientConfig(
                requireValue(
                        firstPresent(
                                preferredClientId,
                                System.getProperty("time.management.microsoftClientId"),
                                System.getenv("TIME_MANAGEMENT_MICROSOFT_CLIENT_ID")
                        ),
                        "A Microsoft OAuth client id is required. Enter it on the Microsoft Drive page or set TIME_MANAGEMENT_MICROSOFT_CLIENT_ID."
                ),
                MICROSOFT_DEFAULT_SCOPES
        );
    }

    public static String defaultGoogleClientId() {
        String fromEnv = firstPresent(
                System.getProperty("time.management.googleClientId"),
                System.getenv("TIME_MANAGEMENT_GOOGLE_CLIENT_ID")
        );
        if (fromEnv != null) {
            return fromEnv;
        }
        Path secretsFile = findGoogleClientSecretsFile();
        if (secretsFile != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(secretsFile.toFile());
                JsonNode app = root.path("installed");
                if (app.isMissingNode()) {
                    app = root.path("web");
                }
                String clientId = app.path("client_id").asText(null);
                if (clientId != null && !clientId.isBlank()) {
                    return clientId.trim();
                }
            } catch (IOException e) {
                System.err.println("Warning: could not read " + secretsFile.getFileName() + ": " + e.getMessage());
            }
        }
        return null;
    }

    public static String defaultMicrosoftClientId() {
        return firstPresent(
                System.getProperty("time.management.microsoftClientId"),
                System.getenv("TIME_MANAGEMENT_MICROSOFT_CLIENT_ID")
        );
    }

    public static List<String> googleDefaultScopes() {
        return GOOGLE_DEFAULT_SCOPES;
    }

    public static List<String> googleLoginScopes() {
        return GOOGLE_LOGIN_SCOPES;
    }

    public static List<String> microsoftDefaultScopes() {
        return MICROSOFT_DEFAULT_SCOPES;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
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
