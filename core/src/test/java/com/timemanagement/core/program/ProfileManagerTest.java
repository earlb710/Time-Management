package com.timemanagement.core.program;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.GoogleIdentity;
import com.timemanagement.core.dataclass.ManagedProfile;
import com.timemanagement.core.dataclass.MicrosoftIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void allowsMultipleProfilesPerSingleGoogleLogin() {
        JsonDataStore dataStore = new JsonDataStore(tempDir);
        GoogleLoginManager loginManager = new GoogleLoginManager(dataStore);
        ProfileManager profileManager = new ProfileManager(dataStore);

        GoogleAccount account = loginManager.login(new GoogleIdentity("google-subject-1", "person@gmail.com", "Person"));

        profileManager.createProfile(account.getAccountId(), "Personal", "person");
        profileManager.createProfile(account.getAccountId(), "Calendar API", "service");

        List<ManagedProfile> profiles = profileManager.listProfiles(account.getAccountId());
        assertEquals(2, profiles.size());
    }

    @Test
    void acceptsGoogleWorkspaceIdentity() {
        JsonDataStore dataStore = new JsonDataStore(tempDir);
        GoogleLoginManager loginManager = new GoogleLoginManager(dataStore);

        GoogleAccount account = loginManager.login(new GoogleIdentity("workspace-subject-1", "person@example.com", "Person"));

        assertEquals("workspace-subject-1", account.getAccountId());
        assertEquals("person@example.com", account.getEmail());
    }

    @Test
    void rejectsMissingSubjectId() {
        JsonDataStore dataStore = new JsonDataStore(tempDir);
        GoogleLoginManager loginManager = new GoogleLoginManager(dataStore);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> loginManager.login(new GoogleIdentity(" ", "person@gmail.com", "Person")));

        assertTrue(error.getMessage().contains("subject id"));
    }

    @Test
    void allowsProfilesForMicrosoftLogin() {
        JsonDataStore dataStore = new JsonDataStore(tempDir);
        MicrosoftLoginManager loginManager = new MicrosoftLoginManager(dataStore);
        ProfileManager profileManager = new ProfileManager(dataStore);

        GoogleAccount account = loginManager.login(new MicrosoftIdentity("aad-subject-1", "person@outlook.com", "Person"));

        profileManager.createProfile(account.getAccountId(), "Work Files", "person");

        List<ManagedProfile> profiles = profileManager.listProfiles(account.getAccountId());
        assertEquals(1, profiles.size());
        assertEquals("microsoft", account.getProvider());
    }
}
