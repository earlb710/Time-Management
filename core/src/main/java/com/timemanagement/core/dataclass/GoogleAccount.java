package com.timemanagement.core.dataclass;

public class GoogleAccount {
    private String accountId;
    private String provider;
    private String email;
    private String displayName;

    public GoogleAccount() {
    }

    public GoogleAccount(String accountId, String provider, String email, String displayName) {
        this.accountId = accountId;
        this.provider = provider;
        this.email = email;
        this.displayName = displayName;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getProvider() {
        return provider;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }
}
