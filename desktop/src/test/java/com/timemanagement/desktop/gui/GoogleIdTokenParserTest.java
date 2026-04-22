package com.timemanagement.desktop.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timemanagement.core.dataclass.GoogleIdentity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleIdTokenParserTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-21T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void parsesIdentityClaimsFromIdToken() {
        GoogleIdTokenParser parser = new GoogleIdTokenParser(new ObjectMapper(), FIXED_CLOCK);

        GoogleIdentity identity = parser.parse(validToken("""
                {
                  "iss":"https://accounts.google.com",
                  "aud":"expected-client-id",
                  "sub":"subject-123",
                  "email":"person@gmail.com",
                  "name":"Person Example",
                  "exp":1893456000
                }
                """), "expected-client-id");

        assertEquals("subject-123", identity.getSubjectId());
        assertEquals("person@gmail.com", identity.getEmail());
        assertEquals("Person Example", identity.getDisplayName());
    }

    @Test
    void fallsBackToEmailWhenNameClaimIsMissing() {
        GoogleIdTokenParser parser = new GoogleIdTokenParser(new ObjectMapper(), FIXED_CLOCK);

        GoogleIdentity identity = parser.parse(validToken("""
                {
                  "iss":"accounts.google.com",
                  "aud":"expected-client-id",
                  "sub":"subject-123",
                  "email":"person@gmail.com",
                  "exp":1893456000
                }
                """), "expected-client-id");

        assertEquals("person@gmail.com", identity.getDisplayName());
    }

    @Test
    void rejectsWrongAudience() {
        GoogleIdTokenParser parser = new GoogleIdTokenParser(new ObjectMapper(), FIXED_CLOCK);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> parser.parse(validToken("""
                        {
                          "iss":"https://accounts.google.com",
                          "aud":"other-client-id",
                          "sub":"subject-123",
                          "email":"person@gmail.com",
                          "name":"Person Example",
                          "exp":1893456000
                        }
                        """), "expected-client-id"));

        assertEquals("Google sign-in returned an ID token for a different client.", error.getMessage());
    }

    private String validToken(String payloadJson) {
        return encode("{\"alg\":\"none\",\"typ\":\"JWT\"}")
                + "."
                + encode(payloadJson)
                + ".signature";
    }

    private String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
