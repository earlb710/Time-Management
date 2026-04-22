package com.timemanagement.core.program;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.TimeEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TimeEntryManager {
    private final JsonDataStore dataStore;

    public TimeEntryManager(JsonDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public TimeEntry createEntry(String profileId, String description, int durationMinutes) {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("Profile id is required.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Time entry description is required.");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Time entry duration must be greater than zero.");
        }

        TimeEntry timeEntry = new TimeEntry(
                UUID.randomUUID().toString(),
                profileId,
                description.trim(),
                durationMinutes,
                Instant.now()
        );

        Map<String, List<TimeEntry>> timeEntriesByProfile = dataStore.loadTimeEntries();
        timeEntriesByProfile.computeIfAbsent(profileId, ignored -> new ArrayList<>()).add(timeEntry);
        dataStore.saveTimeEntries(timeEntriesByProfile);
        return timeEntry;
    }

    public List<TimeEntry> listEntries(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return List.of();
        }
        List<TimeEntry> entries = new ArrayList<>(dataStore.loadTimeEntries().getOrDefault(profileId, List.of()));
        entries.sort(Comparator.comparing(TimeEntry::getStartedAt).reversed());
        return entries;
    }
}
