package com.timemanagement.desktop.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
