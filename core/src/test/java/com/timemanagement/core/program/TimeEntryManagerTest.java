package com.timemanagement.core.program;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.ManagedProfile;
import com.timemanagement.core.dataclass.TimeEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeEntryManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void storesEntriesPerProfile() {
        JsonDataStore dataStore = new JsonDataStore(tempDir);
        LocalStorageAccountManager accountManager = new LocalStorageAccountManager(dataStore);
        ProfileManager profileManager = new ProfileManager(dataStore);
        TimeEntryManager timeEntryManager = new TimeEntryManager(dataStore);

        ManagedProfile profile = profileManager.createProfile(
                accountManager.useLocalStorage().getAccountId(),
                "Project Alpha",
                "person"
        );

        timeEntryManager.createEntry(profile.getProfileId(), "Planning", 30);
        timeEntryManager.createEntry(profile.getProfileId(), "Review", 45);

        List<TimeEntry> entries = timeEntryManager.listEntries(profile.getProfileId());
        assertEquals(2, entries.size());
        assertEquals("Review", entries.get(0).getDescription());
        assertEquals("Planning", entries.get(1).getDescription());
    }

    @Test
    void rejectsInvalidEntryInput() {
        JsonDataStore dataStore = new JsonDataStore(tempDir);
        TimeEntryManager timeEntryManager = new TimeEntryManager(dataStore);

        assertThrows(IllegalArgumentException.class, () -> timeEntryManager.createEntry("profile-1", " ", 15));
        assertThrows(IllegalArgumentException.class, () -> timeEntryManager.createEntry("profile-1", "Planning", 0));
    }
}
