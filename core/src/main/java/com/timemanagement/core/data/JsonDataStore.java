package com.timemanagement.core.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.ManagedProfile;
import com.timemanagement.core.dataclass.TimeEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonDataStore {
    private final Path accountsPath;
    private final Path profilesPath;
    private final Path timeEntriesPath;
    private final ObjectMapper mapper;

    public JsonDataStore(Path dataDirectory) {
        this.accountsPath = dataDirectory.resolve("accounts.json");
        this.profilesPath = dataDirectory.resolve("profiles.json");
        this.timeEntriesPath = dataDirectory.resolve("time-entries.json");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Map<String, GoogleAccount> loadAccounts() {
        ensureFilesExist();
        try {
            List<GoogleAccount> accounts = mapper.readValue(accountsPath.toFile(), new TypeReference<>() {});
            Map<String, GoogleAccount> byId = new LinkedHashMap<>();
            for (GoogleAccount account : accounts) {
                byId.put(account.getAccountId(), account);
            }
            return byId;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read accounts.json", e);
        }
    }

    public void saveAccounts(Map<String, GoogleAccount> accounts) {
        ensureFilesExist();
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(accountsPath.toFile(), new ArrayList<>(accounts.values()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not write accounts.json", e);
        }
    }

    public Map<String, List<ManagedProfile>> loadProfiles() {
        ensureFilesExist();
        try {
            return mapper.readValue(profilesPath.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Could not read profiles.json", e);
        }
    }

    public void saveProfiles(Map<String, List<ManagedProfile>> profilesByAccount) {
        ensureFilesExist();
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(profilesPath.toFile(), profilesByAccount);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write profiles.json", e);
        }
    }

    public Map<String, List<TimeEntry>> loadTimeEntries() {
        ensureFilesExist();
        try {
            return mapper.readValue(timeEntriesPath.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Could not read time-entries.json", e);
        }
    }

    public void saveTimeEntries(Map<String, List<TimeEntry>> timeEntriesByProfile) {
        ensureFilesExist();
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(timeEntriesPath.toFile(), timeEntriesByProfile);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write time-entries.json", e);
        }
    }

    private void ensureFilesExist() {
        try {
            Files.createDirectories(accountsPath.getParent());
            if (!Files.exists(accountsPath)) {
                Files.writeString(accountsPath, "[]");
            }
            if (!Files.exists(profilesPath)) {
                Files.writeString(profilesPath, "{}");
            }
            if (!Files.exists(timeEntriesPath)) {
                Files.writeString(timeEntriesPath, "{}");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare data directory", e);
        }
    }
}
