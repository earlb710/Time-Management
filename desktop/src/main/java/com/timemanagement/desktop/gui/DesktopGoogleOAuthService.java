package com.timemanagement.desktop.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timemanagement.core.dataclass.GoogleIdentity;
import com.timemanagement.core.dataclass.GoogleOAuthSession;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DesktopGoogleOAuthService {
    private static final String CALLBACK_PATH = "/oauth2/callback";

    private final DesktopOAuthClientConfig config;
    private final DesktopOAuthCredentialStore<GoogleOAuthSession> credentialStore;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final GoogleIdTokenParser idTokenParser;

    public DesktopGoogleOAuthService(DesktopOAuthClientConfig config, DesktopOAuthCredentialStore<GoogleOAuthSession> credentialStore) {
        this(config, credentialStore, HttpClient.newHttpClient(), new ObjectMapper(), new SecureRandom(), Clock.systemUTC());
    }

    DesktopGoogleOAuthService(DesktopOAuthClientConfig config,
                              DesktopOAuthCredentialStore<GoogleOAuthSession> credentialStore,
                              HttpClient httpClient,
                              ObjectMapper mapper,
                              SecureRandom secureRandom,
                              Clock clock) {
        this.config = config;
        this.credentialStore = credentialStore;
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.secureRandom = secureRandom;
        this.clock = clock;
        this.idTokenParser = new GoogleIdTokenParser(mapper, clock);
    }

    public GoogleOAuthSession signIn(char[] passphrase) {
        OAuthCallback callback = awaitAuthorizationCode(true);
        GoogleOAuthSession session = exchangeAuthorizationCode(callback, true);
        credentialStore.save(session, passphrase);
        return session;
    }

    public GoogleIdentity signInForLogin() {
        return exchangeAuthorizationCode(awaitAuthorizationCode(false), false).getIdentity();
    }

    public Optional<GoogleOAuthSession> restoreSession(char[] passphrase) {
        Optional<GoogleOAuthSession> existing = credentialStore.load(passphrase);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        GoogleOAuthSession session = existing.get();
        if (session.requiresRefresh(clock)) {
            session = refreshSession(session);
            credentialStore.save(session, passphrase);
        }
        return Optional.of(session);
    }

    public void signOut() {
        credentialStore.clear();
    }

    private OAuthCallback awaitAuthorizationCode(boolean requestOfflineAccess) {
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = codeChallengeForVerifier(codeVerifier);
        CompletableFuture<Map<String, String>> paramsFuture = new CompletableFuture<>();

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the local OAuth callback server.", e);
        }
        int port = server.getAddress().getPort();
        String redirectUri = "http://127.0.0.1:" + port + CALLBACK_PATH;
        server.createContext(CALLBACK_PATH, exchange -> handleCallback(exchange, paramsFuture));
        server.start();

        try {
            openBrowser(buildAuthorizationUri(redirectUri, codeChallenge, requestOfflineAccess));
            Map<String, String> params = paramsFuture.get(5, TimeUnit.MINUTES);
            if (params.containsKey("error")) {
                throw new IllegalStateException("Google sign-in was not approved: " + params.get("error"));
            }
            String code = params.get("code");
            if (code == null || code.isBlank()) {
                throw new IllegalStateException("Google sign-in did not return an authorization code.");
            }
            return new OAuthCallback(code, codeVerifier, redirectUri);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google sign-in was interrupted.", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Could not complete Google sign-in.", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out waiting for Google sign-in to complete.", e);
        } finally {
            server.stop(0);
        }
    }

    private void handleCallback(HttpExchange exchange, CompletableFuture<Map<String, String>> paramsFuture) throws IOException {
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
        byte[] response = """
                <html><body><h2>Google sign-in complete</h2><p>You can return to the Time Management app.</p></body></html>
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
        paramsFuture.complete(params);
    }

    private URI buildAuthorizationUri(String redirectUri, String codeChallenge, boolean requestOfflineAccess) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", config.getClientId());
        params.put("redirect_uri", redirectUri);
        params.put("response_type", "code");
        params.put("scope", String.join(" ", config.getScopes()));
        if (requestOfflineAccess) {
            params.put("access_type", "offline");
            params.put("include_granted_scopes", "true");
            params.put("prompt", "consent");
        }
        params.put("code_challenge", codeChallenge);
        params.put("code_challenge_method", "S256");
        return URI.create("https://accounts.google.com/o/oauth2/v2/auth?" + toFormBody(params));
    }

    private GoogleOAuthSession exchangeAuthorizationCode(OAuthCallback callback, boolean requireRefreshToken) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("code", callback.code());
        params.put("client_id", config.getClientId());
        params.put("redirect_uri", callback.redirectUri());
        params.put("grant_type", "authorization_code");
        params.put("code_verifier", callback.codeVerifier());
        JsonNode tokenResponse = postForm("https://oauth2.googleapis.com/token", params, "Could not exchange the Google authorization code.");

        String accessToken = requiredField(tokenResponse, "access_token", "Google sign-in did not return an access token.");
        String refreshToken = requireRefreshToken
                ? requiredField(tokenResponse, "refresh_token", "Google sign-in did not return a refresh token.")
                : optionalField(tokenResponse, "refresh_token");
        Instant expiresAt = clock.instant().plusSeconds(tokenResponse.path("expires_in").asLong(3600));
        GoogleIdentity identity = idTokenParser.parse(
                requiredField(tokenResponse, "id_token", "Google sign-in did not return an ID token."),
                config.getClientId()
        );
        return new GoogleOAuthSession(identity, accessToken, refreshToken, expiresAt, new ArrayList<>(config.getScopes()));
    }

    private GoogleOAuthSession refreshSession(GoogleOAuthSession session) {
        String refreshToken = session.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException("The saved Google session cannot be refreshed because no refresh token is available.");
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", config.getClientId());
        params.put("refresh_token", refreshToken);
        params.put("grant_type", "refresh_token");
        JsonNode tokenResponse = postForm("https://oauth2.googleapis.com/token", params, "Could not refresh the Google access token.");

        String accessToken = requiredField(tokenResponse, "access_token", "Google token refresh did not return an access token.");
        Instant expiresAt = clock.instant().plusSeconds(tokenResponse.path("expires_in").asLong(3600));
        List<String> scopes = session.getScopes();
        if (tokenResponse.hasNonNull("scope")) {
            scopes = List.of(tokenResponse.get("scope").asText().split("\\s+"));
        }
        return new GoogleOAuthSession(session.getIdentity(), accessToken, refreshToken, expiresAt, scopes);
    }

    private JsonNode postForm(String url, Map<String, String> params, String errorMessage) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(toFormBody(params)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(errorMessage + " Google returned status " + response.statusCode() + ".");
            }
            return mapper.readTree(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException(errorMessage, e);
        }
    }

    private String requiredField(JsonNode node, String name, String errorMessage) {
        if (!node.hasNonNull(name) || node.get(name).asText().isBlank()) {
            throw new IllegalStateException(errorMessage);
        }
        return node.get(name).asText();
    }

    private String optionalField(JsonNode node, String name) {
        if (!node.hasNonNull(name) || node.get(name).asText().isBlank()) {
            return null;
        }
        return node.get(name).asText();
    }

    private String toFormBody(Map<String, String> params) {
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            entries.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return String.join("&", entries);
    }

    private void openBrowser(URI uri) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException("Open this URL in a browser to continue Google sign-in: " + uri);
            }
            Desktop.getDesktop().browse(uri);
        } catch (IOException e) {
            throw new IllegalStateException("Could not open the browser for Google sign-in. Open this URL manually: " + uri, e);
        }
    }

    private String generateCodeVerifier() {
        byte[] verifierBytes = new byte[32];
        secureRandom.nextBytes(verifierBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);
    }

    private String codeChallengeForVerifier(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available for PKCE.", e);
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";
            params.put(key, value);
        }
        return params;
    }

    private String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    record OAuthCallback(String code, String codeVerifier, String redirectUri) {
    }
}
