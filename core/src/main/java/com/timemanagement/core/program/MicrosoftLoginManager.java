package com.timemanagement.core.program;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.MicrosoftIdentity;

import java.util.Map;

public class MicrosoftLoginManager {
    private static final String PROVIDER = "microsoft";

    private final JsonDataStore dataStore;

    public MicrosoftLoginManager(JsonDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public GoogleAccount login(MicrosoftIdentity identity) {
        if (identity == null) {
            throw new IllegalArgumentException("Microsoft sign-in did not return an identity.");
        }
        String accountId = PROVIDER + ":" + requireValue(identity.getSubjectId(), "Microsoft sign-in did not return a subject id.");
        String email = requireValue(identity.getEmail(), "Microsoft sign-in did not return an email address.").toLowerCase();
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
