package com.timemanagement.desktop.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timemanagement.core.dataclass.GoogleIdentity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;

final class GoogleIdTokenParser {
    private static final String GOOGLE_ISSUER = "accounts.google.com";
    private static final String GOOGLE_HTTPS_ISSUER = "https://accounts.google.com";

    private final ObjectMapper mapper;
    private final Clock clock;

    GoogleIdTokenParser(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    GoogleIdentity parse(String idToken, String expectedAudience) {
        JsonNode payload = decodePayload(idToken);
        validateIssuer(payload.path("iss").asText(""));
        validateAudience(payload.path("aud"), expectedAudience);
        validateExpiration(payload.path("exp").asLong(0L));

        String subjectId = requiredClaim(payload, "sub", "Google sign-in did not return a subject id.");
        String email = requiredClaim(payload, "email", "Google sign-in did not return an email address.");
        String displayName = payload.hasNonNull("name") && !payload.get("name").asText().isBlank()
                ? payload.get("name").asText().trim()
                : email;
        return new GoogleIdentity(subjectId, email, displayName);
    }

    private JsonNode decodePayload(String idToken) {
        String normalizedToken = requireValue(idToken, "Google sign-in did not return an ID token.");
        String[] parts = normalizedToken.split("\\.");
        if (parts.length < 2) {
            throw new IllegalStateException("Google sign-in returned an unreadable ID token.");
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(withPadding(parts[1]));
            return mapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | IOException e) {
            throw new IllegalStateException("Google sign-in returned an unreadable ID token.", e);
        }
    }

    private void validateIssuer(String issuer) {
        if (!GOOGLE_ISSUER.equals(issuer) && !GOOGLE_HTTPS_ISSUER.equals(issuer)) {
            throw new IllegalStateException("Google sign-in returned an ID token from an unexpected issuer.");
        }
    }

    private void validateAudience(JsonNode audienceNode, String expectedAudience) {
        String audience = requireValue(expectedAudience, "A Google web client id is required.");
        if (audienceNode == null || audienceNode.isNull()) {
            throw new IllegalStateException("Google sign-in returned an ID token without an audience.");
        }
        if (audienceNode.isArray()) {
            for (JsonNode candidate : audienceNode) {
                if (audience.equals(candidate.asText())) {
                    return;
                }
            }
        } else if (audience.equals(audienceNode.asText())) {
            return;
        }
        throw new IllegalStateException("Google sign-in returned an ID token for a different client.");
    }

    private void validateExpiration(long expiresAtEpochSeconds) {
        if (expiresAtEpochSeconds <= clock.instant().getEpochSecond()) {
            throw new IllegalStateException("Google sign-in returned an expired ID token.");
        }
    }

    private String requiredClaim(JsonNode payload, String claimName, String message) {
        if (!payload.hasNonNull(claimName) || payload.get(claimName).asText().isBlank()) {
            throw new IllegalStateException(message);
        }
        return payload.get(claimName).asText().trim();
    }

    private String requireValue(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    private String withPadding(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "=".repeat(4 - remainder);
    }
}
