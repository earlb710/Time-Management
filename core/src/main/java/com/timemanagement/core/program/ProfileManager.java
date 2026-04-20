package com.timemanagement.core.program;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.ManagedProfile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProfileManager {
    private final JsonDataStore dataStore;

    public ProfileManager(JsonDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public ManagedProfile createProfile(String accountId, String profileName, String profileType) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account id is required.");
        }
        if (profileName == null || profileName.isBlank()) {
            throw new IllegalArgumentException("Profile name is required.");
        }
        if (profileType == null || profileType.isBlank()) {
            throw new IllegalArgumentException("Profile type is required.");
        }

        ManagedProfile profile = new ManagedProfile(
                UUID.randomUUID().toString(),
                accountId,
                profileName.trim(),
                profileType.trim(),
                Instant.now()
        );

        Map<String, List<ManagedProfile>> profilesByAccount = dataStore.loadProfiles();
        profilesByAccount.computeIfAbsent(accountId, key -> new ArrayList<>()).add(profile);
        dataStore.saveProfiles(profilesByAccount);
        return profile;
    }

    public List<ManagedProfile> listProfiles(String accountId) {
        Map<String, List<ManagedProfile>> profilesByAccount = dataStore.loadProfiles();
        return new ArrayList<>(profilesByAccount.getOrDefault(accountId, List.of()));
    }
}
