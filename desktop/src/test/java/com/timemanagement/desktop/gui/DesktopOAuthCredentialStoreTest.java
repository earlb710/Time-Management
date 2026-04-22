package com.timemanagement.desktop.gui;

import com.timemanagement.core.dataclass.GoogleIdentity;
import com.timemanagement.core.dataclass.GoogleOAuthSession;
import com.timemanagement.core.dataclass.MicrosoftIdentity;
import com.timemanagement.core.dataclass.MicrosoftOAuthSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopOAuthCredentialStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsEncryptedGoogleSession() {
        DesktopOAuthCredentialStore<GoogleOAuthSession> store = new DesktopOAuthCredentialStore<>(tempDir.resolve("session.enc"), GoogleOAuthSession.class, "Google OAuth");
        GoogleOAuthSession session = new GoogleOAuthSession(
                new GoogleIdentity("subject-1", "person@gmail.com", "Person"),
                "access-token",
                "refresh-token",
                Instant.parse("2026-04-20T07:00:00Z"),
                List.of("openid", "email")
        );

        store.save(session, "passphrase".toCharArray());
        GoogleOAuthSession loaded = store.load("passphrase".toCharArray()).orElseThrow();

        assertEquals("subject-1", loaded.getIdentity().getSubjectId());
        assertEquals("refresh-token", loaded.getRefreshToken());
    }

    @Test
    void rejectsWrongPassphrase() {
        DesktopOAuthCredentialStore<GoogleOAuthSession> store = new DesktopOAuthCredentialStore<>(tempDir.resolve("session.enc"), GoogleOAuthSession.class, "Google OAuth");
        store.save(new GoogleOAuthSession(
                new GoogleIdentity("subject-1", "person@gmail.com", "Person"),
                "access-token",
                "refresh-token",
                Instant.parse("2026-04-20T07:00:00Z"),
                List.of("openid", "email")
        ), "passphrase".toCharArray());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> store.load("wrong-passphrase".toCharArray()));

        assertTrue(error.getMessage().contains("unlock"));
    }

    @Test
    void savesAndLoadsEncryptedMicrosoftSession() {
        DesktopOAuthCredentialStore<MicrosoftOAuthSession> store = new DesktopOAuthCredentialStore<>(
                tempDir.resolve("microsoft-session.enc"),
                MicrosoftOAuthSession.class,
                "Microsoft OAuth"
        );
        MicrosoftOAuthSession session = new MicrosoftOAuthSession(
                new MicrosoftIdentity("subject-2", "person@outlook.com", "Person"),
                "access-token",
                "refresh-token",
                Instant.parse("2026-04-20T07:00:00Z"),
                List.of("openid", "User.Read")
        );

        store.save(session, "passphrase".toCharArray());
        MicrosoftOAuthSession loaded = store.load("passphrase".toCharArray()).orElseThrow();

        assertEquals("subject-2", loaded.getIdentity().getSubjectId());
        assertEquals("refresh-token", loaded.getRefreshToken());
    }
}
