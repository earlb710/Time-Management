package com.timemanagement.core.dataclass;

import java.time.Instant;

public class ManagedProfile {
    private String profileId;
    private String accountId;
    private String profileName;
    private String profileType;
    private Instant createdAt;

    public ManagedProfile() {
    }

    public ManagedProfile(String profileId, String accountId, String profileName, String profileType, Instant createdAt) {
        this.profileId = profileId;
        this.accountId = accountId;
        this.profileName = profileName;
        this.profileType = profileType;
        this.createdAt = createdAt;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getProfileName() {
        return profileName;
    }

    public String getProfileType() {
        return profileType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
