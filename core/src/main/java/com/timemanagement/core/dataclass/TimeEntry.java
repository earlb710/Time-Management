package com.timemanagement.core.dataclass;

import java.time.Instant;

public class TimeEntry {
    private String entryId;
    private String profileId;
    private String description;
    private int durationMinutes;
    private Instant startedAt;

    public TimeEntry() {
    }

    public TimeEntry(String entryId, String profileId, String description, int durationMinutes, Instant startedAt) {
        this.entryId = entryId;
        this.profileId = profileId;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.startedAt = startedAt;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
