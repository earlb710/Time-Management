package com.timemanagement.desktop.gui;

import com.timemanagement.core.dataclass.GoogleIdentity;
import com.timemanagement.core.dataclass.GoogleOAuthSession;
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
        DesktopOAuthCredentialStore store = new DesktopOAuthCredentialStore(tempDir.resolve("session.enc"));
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
        DesktopOAuthCredentialStore store = new DesktopOAuthCredentialStore(tempDir.resolve("session.enc"));
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
}
