package com.timemanagement.core.program;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.GoogleIdentity;

import java.util.Map;

public class GoogleLoginManager {
    private static final String PROVIDER = "google";

    private final JsonDataStore dataStore;

    public GoogleLoginManager(JsonDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public GoogleAccount login(GoogleIdentity identity) {
        if (identity == null) {
            throw new IllegalArgumentException("Google sign-in did not return an identity.");
        }
        String accountId = requireValue(identity.getSubjectId(), "Google sign-in did not return a subject id.");
        String email = requireValue(identity.getEmail(), "Google sign-in did not return an email address.").toLowerCase();
        String displayName = normalizeDisplayName(identity.getDisplayName(), email);

        Map<String, GoogleAccount> accounts = dataStore.loadAccounts();
        GoogleAccount account = accounts.get(accountId);
        if (account == null) {
            account = findExistingAccount(accounts, email);
        }
        if (account == null) {
            account = new GoogleAccount(accountId, PROVIDER, email, displayName);
            accounts.put(accountId, account);
            dataStore.saveAccounts(accounts);
        }
        return account;
    }

    private GoogleAccount findExistingAccount(Map<String, GoogleAccount> accounts, String email) {
        for (GoogleAccount account : accounts.values()) {
            if (PROVIDER.equals(account.getProvider()) && email.equalsIgnoreCase(account.getEmail())) {
                return account;
            }
        }
        return null;
    }

    private String requireValue(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeDisplayName(String displayName, String email) {
        return displayName == null || displayName.isBlank() ? email : displayName.trim();
    }
}
