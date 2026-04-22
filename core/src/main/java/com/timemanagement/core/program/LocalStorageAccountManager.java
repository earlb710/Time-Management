package com.timemanagement.core.program;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.GoogleAccount;

import java.util.Map;

public class LocalStorageAccountManager {
    public static final String ACCOUNT_ID = "local-storage";
    public static final String PROVIDER = "local";
    public static final String EMAIL = "local@time-management";
    public static final String DISPLAY_NAME = "Local storage";

    private final JsonDataStore dataStore;

    public LocalStorageAccountManager(JsonDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public GoogleAccount useLocalStorage() {
        Map<String, GoogleAccount> accounts = dataStore.loadAccounts();
        GoogleAccount account = accounts.get(ACCOUNT_ID);
        if (account == null) {
            account = new GoogleAccount(ACCOUNT_ID, PROVIDER, EMAIL, DISPLAY_NAME);
            accounts.put(ACCOUNT_ID, account);
            dataStore.saveAccounts(accounts);
        }
        return account;
    }
}
