package com.timemanagement.core.program;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.GoogleAccount;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

public class GoogleLoginManager {
    private final JsonDataStore dataStore;

    public GoogleLoginManager(JsonDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public GoogleAccount login(String email, String displayName) {
        String normalizedEmail = requireGoogleEmail(email);
        String accountId = googleIdForEmail(normalizedEmail);

        Map<String, GoogleAccount> accounts = dataStore.loadAccounts();
        GoogleAccount account = accounts.get(accountId);
        if (account == null) {
            account = new GoogleAccount(accountId, normalizedEmail, displayName == null || displayName.isBlank() ? normalizedEmail : displayName.trim());
            accounts.put(accountId, account);
            dataStore.saveAccounts(accounts);
        }
        return account;
    }

    private String requireGoogleEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Google login requires an email address.");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !(normalized.endsWith("@gmail.com") || normalized.endsWith("@googlemail.com"))) {
            throw new IllegalArgumentException("Use a Google account email (gmail.com/googlemail.com).");
        }
        return normalized;
    }

    private String googleIdForEmail(String normalizedEmail) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(normalizedEmail.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
