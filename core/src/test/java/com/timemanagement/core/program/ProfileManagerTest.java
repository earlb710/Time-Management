package com.timemanagement.core.program;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.GoogleIdentity;
import com.timemanagement.core.dataclass.ManagedProfile;
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
}
