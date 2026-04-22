package com.timemanagement.android.gui;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.program.GoogleLoginManager;
import com.timemanagement.core.program.MicrosoftLoginManager;
import com.timemanagement.core.program.ProfileManager;

import java.nio.file.Path;

public final class AndroidPlaceholderApp {
    private AndroidPlaceholderApp() {
    }

    public static void main(String[] args) {
        Path dataDir = Path.of("data");
        JsonDataStore dataStore = new JsonDataStore(dataDir);
        AndroidGuiPlaceholder app = new AndroidGuiPlaceholder(
                new GoogleLoginManager(dataStore),
                new MicrosoftLoginManager(dataStore),
                new ProfileManager(dataStore)
        );

        System.out.println("Time Management Android placeholder started.");
        System.out.println("Data directory: " + dataDir.toAbsolutePath());
        System.out.println("Profiles for sample account: " + app.listProfiles("sample-account").size());
        System.out.println("Replace this launcher with a real Android Activity when the Android app is implemented.");
    }
}
