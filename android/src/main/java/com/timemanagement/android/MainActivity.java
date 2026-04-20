package com.timemanagement.android;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.ManagedProfile;
import com.timemanagement.core.program.LocalStorageAccountManager;
import com.timemanagement.core.program.ProfileManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String STORAGE_PREFS = "storage-setup";
    private static final String GOOGLE_CLIENT_ID_KEY = "google-client-id";
    private static final String MICROSOFT_CLIENT_ID_KEY = "microsoft-client-id";

    private ProfileManager profileManager;
    private LocalStorageAccountManager localStorageAccountManager;
    private SharedPreferences preferences;
    private String currentAccountId;

    private TextView storageStatusLabel;
    private TextView accountLabel;
    private EditText profileNameField;
    private EditText profileTypeField;
    private ListView profileListView;
    private TextView googleSetupStatus;
    private TextView microsoftSetupStatus;
    private EditText googleClientIdField;
    private EditText googlePassphraseField;
    private EditText microsoftClientIdField;
    private EditText microsoftPassphraseField;
    private View localStorageSection;
    private View googleDriveSection;
    private View microsoftDriveSection;
    private ArrayAdapter<String> profileAdapter;
    private final List<String> profileItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Path dataDir = getFilesDir().toPath().resolve("data");
        JsonDataStore dataStore = new JsonDataStore(dataDir);
        profileManager = new ProfileManager(dataStore);
        localStorageAccountManager = new LocalStorageAccountManager(dataStore);
        preferences = getSharedPreferences(STORAGE_PREFS, MODE_PRIVATE);

        storageStatusLabel = findViewById(R.id.storageStatusLabel);
        accountLabel = findViewById(R.id.accountLabel);
        profileNameField = findViewById(R.id.profileNameField);
        profileTypeField = findViewById(R.id.profileTypeField);
        profileListView = findViewById(R.id.profileListView);
        googleSetupStatus = findViewById(R.id.googleSetupStatus);
        microsoftSetupStatus = findViewById(R.id.microsoftSetupStatus);
        googleClientIdField = findViewById(R.id.googleClientIdField);
        googlePassphraseField = findViewById(R.id.googlePassphraseField);
        microsoftClientIdField = findViewById(R.id.microsoftClientIdField);
        microsoftPassphraseField = findViewById(R.id.microsoftPassphraseField);
        localStorageSection = findViewById(R.id.localStorageSection);
        googleDriveSection = findViewById(R.id.googleDriveSection);
        microsoftDriveSection = findViewById(R.id.microsoftDriveSection);

        profileAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, profileItems);
        profileListView.setAdapter(profileAdapter);

        GoogleAccount localAccount = localStorageAccountManager.useLocalStorage();
        currentAccountId = localAccount.getAccountId();
        accountLabel.setText(getString(R.string.signed_in_as, currentAccountId));
        refreshProfiles();
        loadSavedSetup();

        Button addProfileButton = findViewById(R.id.addProfileButton);
        addProfileButton.setOnClickListener(v -> addProfile());
        findViewById(R.id.saveGoogleSetupButton).setOnClickListener(v -> saveGoogleSetup());
        findViewById(R.id.clearGoogleSetupButton).setOnClickListener(v -> clearGoogleSetup());
        findViewById(R.id.saveMicrosoftSetupButton).setOnClickListener(v -> saveMicrosoftSetup());
        findViewById(R.id.clearMicrosoftSetupButton).setOnClickListener(v -> clearMicrosoftSetup());

        showSection(localStorageSection, getString(R.string.local_storage_default_status));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.storage_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menuLocalStorage) {
            showSection(localStorageSection, getString(R.string.local_storage_default_status));
            return true;
        }
        if (itemId == R.id.menuConnectGoogleDrive) {
            showSection(googleDriveSection, getString(R.string.google_drive_title));
            return true;
        }
        if (itemId == R.id.menuConnectMicrosoftDrive) {
            showSection(microsoftDriveSection, getString(R.string.microsoft_drive_title));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSection(View visibleSection, String statusText) {
        localStorageSection.setVisibility(visibleSection == localStorageSection ? View.VISIBLE : View.GONE);
        googleDriveSection.setVisibility(visibleSection == googleDriveSection ? View.VISIBLE : View.GONE);
        microsoftDriveSection.setVisibility(visibleSection == microsoftDriveSection ? View.VISIBLE : View.GONE);
        storageStatusLabel.setText(statusText);
    }

    private void addProfile() {
        String name = profileNameField.getText().toString().trim();
        String type = profileTypeField.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_profile_name_required), Toast.LENGTH_SHORT).show();
            return;
        }
        if (type.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_profile_type_required), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            profileManager.createProfile(currentAccountId, name, type);
            profileNameField.setText("");
            profileTypeField.setText("");
            refreshProfiles();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveGoogleSetup() {
        String clientId = googleClientIdField.getText().toString().trim();
        String passphrase = googlePassphraseField.getText().toString();
        if (clientId.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_google_client_id_required), Toast.LENGTH_SHORT).show();
            return;
        }
        if (passphrase.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_google_passphrase_required), Toast.LENGTH_SHORT).show();
            return;
        }

        preferences.edit().putString(GOOGLE_CLIENT_ID_KEY, clientId).apply();
        googlePassphraseField.setText("");
        googleSetupStatus.setText(getString(R.string.google_drive_setup_saved));
        Toast.makeText(this, getString(R.string.google_drive_setup_saved), Toast.LENGTH_LONG).show();
    }

    private void clearGoogleSetup() {
        preferences.edit().remove(GOOGLE_CLIENT_ID_KEY).apply();
        googleClientIdField.setText("");
        googlePassphraseField.setText("");
        googleSetupStatus.setText(getString(R.string.google_drive_setup_cleared));
        Toast.makeText(this, getString(R.string.google_drive_setup_cleared), Toast.LENGTH_SHORT).show();
    }

    private void saveMicrosoftSetup() {
        String clientId = microsoftClientIdField.getText().toString().trim();
        String passphrase = microsoftPassphraseField.getText().toString();
        if (clientId.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_microsoft_client_id_required), Toast.LENGTH_SHORT).show();
            return;
        }
        if (passphrase.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_microsoft_passphrase_required), Toast.LENGTH_SHORT).show();
            return;
        }

        preferences.edit().putString(MICROSOFT_CLIENT_ID_KEY, clientId).apply();
        microsoftPassphraseField.setText("");
        microsoftSetupStatus.setText(getString(R.string.microsoft_drive_setup_saved));
        Toast.makeText(this, getString(R.string.microsoft_drive_setup_saved), Toast.LENGTH_LONG).show();
    }

    private void clearMicrosoftSetup() {
        preferences.edit().remove(MICROSOFT_CLIENT_ID_KEY).apply();
        microsoftClientIdField.setText("");
        microsoftPassphraseField.setText("");
        microsoftSetupStatus.setText(getString(R.string.microsoft_drive_setup_cleared));
        Toast.makeText(this, getString(R.string.microsoft_drive_setup_cleared), Toast.LENGTH_SHORT).show();
    }

    private void loadSavedSetup() {
        googleClientIdField.setText(preferences.getString(GOOGLE_CLIENT_ID_KEY, ""));
        microsoftClientIdField.setText(preferences.getString(MICROSOFT_CLIENT_ID_KEY, ""));
    }

    private void refreshProfiles() {
        profileItems.clear();
        List<ManagedProfile> profiles = profileManager.listProfiles(currentAccountId);
        for (ManagedProfile profile : profiles) {
            profileItems.add(profile.getProfileName() + " (" + profile.getProfileType() + ")");
        }
        profileAdapter.notifyDataSetChanged();
    }
}
