package com.timemanagement.android;

import android.content.Intent;
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
import androidx.appcompat.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.GoogleIdentity;
import com.timemanagement.core.dataclass.ManagedProfile;
import com.timemanagement.core.program.GoogleLoginManager;
import com.timemanagement.core.program.LocalStorageAccountManager;
import com.timemanagement.core.program.ProfileManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainActivity extends AppCompatActivity {
    private static final String STORAGE_PREFS = "storage-setup";
    private static final String INITIAL_LOGIN_PROMPT_COMPLETED_KEY = "initial-login-prompt-completed";
    private static final String MICROSOFT_CLIENT_ID_KEY = "microsoft-client-id";

    private GoogleLoginManager googleLoginManager;
    private ProfileManager profileManager;
    private LocalStorageAccountManager localStorageAccountManager;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private SharedPreferences preferences;
    private Path dataDirectory;
    private GoogleAccount currentAccount;

    private TextView storageStatusLabel;
    private TextView accountLabel;
    private EditText dataDirectoryLabel;
    private EditText profileNameField;
    private EditText profileTypeField;
    private ListView profileListView;
    private TextView googleSetupStatus;
    private TextView microsoftSetupStatus;
    private EditText microsoftClientIdField;
    private EditText microsoftPassphraseField;
    private Button signOutGoogleButton;
    private View localStorageSection;
    private View googleDriveSection;
    private View microsoftDriveSection;
    private ArrayAdapter<String> profileAdapter;
    private final List<String> profileItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleGoogleSignInResult(result.getData())
        );
        setContentView(R.layout.activity_main);

        dataDirectory = getFilesDir().toPath().resolve("data");
        JsonDataStore dataStore = new JsonDataStore(dataDirectory);
        googleLoginManager = new GoogleLoginManager(dataStore);
        profileManager = new ProfileManager(dataStore);
        localStorageAccountManager = new LocalStorageAccountManager(dataStore);
        googleSignInClient = GoogleSignIn.getClient(
                this,
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .build()
        );
        preferences = getSharedPreferences(STORAGE_PREFS, MODE_PRIVATE);

        storageStatusLabel = findViewById(R.id.storageStatusLabel);
        accountLabel = findViewById(R.id.accountLabel);
        dataDirectoryLabel = findViewById(R.id.dataDirectoryLabel);
        profileNameField = findViewById(R.id.profileNameField);
        profileTypeField = findViewById(R.id.profileTypeField);
        profileListView = findViewById(R.id.profileListView);
        googleSetupStatus = findViewById(R.id.googleSetupStatus);
        microsoftSetupStatus = findViewById(R.id.microsoftSetupStatus);
        microsoftClientIdField = findViewById(R.id.microsoftClientIdField);
        microsoftPassphraseField = findViewById(R.id.microsoftPassphraseField);
        signOutGoogleButton = findViewById(R.id.signOutGoogleButton);
        localStorageSection = findViewById(R.id.localStorageSection);
        googleDriveSection = findViewById(R.id.googleDriveSection);
        microsoftDriveSection = findViewById(R.id.microsoftDriveSection);

        profileAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, profileItems);
        profileListView.setAdapter(profileAdapter);

        updateActiveAccount(localStorageAccountManager.useLocalStorage());
        loadSavedSetup();
        refreshGoogleSignInState();

        Button addProfileButton = findViewById(R.id.addProfileButton);
        addProfileButton.setOnClickListener(v -> addProfile());
        findViewById(R.id.browseDataDirectoryButton).setOnClickListener(v -> browseDataDirectory());
        findViewById(R.id.signInWithGoogleButton).setOnClickListener(v -> signInWithGoogle());
        signOutGoogleButton.setOnClickListener(v -> signOutGoogle());
        findViewById(R.id.saveMicrosoftSetupButton).setOnClickListener(v -> saveMicrosoftSetup());
        findViewById(R.id.clearMicrosoftSetupButton).setOnClickListener(v -> clearMicrosoftSetup());

        dataDirectoryLabel.setText(dataDirectory.toAbsolutePath().normalize().toString());
        showSection(localStorageSection, getString(R.string.local_storage_default_status));
        maybePromptForInitialLogin();
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
            refreshGoogleSignInState();
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
            profileManager.createProfile(currentAccount.getAccountId(), name, type);
            profileNameField.setText("");
            profileTypeField.setText("");
            refreshProfiles();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void signInWithGoogle() {
        googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
    }

    private void handleGoogleSignInResult(Intent data) {
        try {
            GoogleSignInAccount signedInAccount = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException.class);
            if (signedInAccount == null || signedInAccount.getId() == null || signedInAccount.getEmail() == null) {
                throw new IllegalStateException(getString(R.string.error_google_sign_in_failed));
            }

            GoogleAccount account = googleLoginManager.login(new GoogleIdentity(
                    signedInAccount.getId(),
                    signedInAccount.getEmail(),
                    signedInAccount.getDisplayName()
            ));
            updateActiveAccount(account);
            googleSetupStatus.setText(getString(R.string.google_drive_connected_as, describeAccount(account)));
            signOutGoogleButton.setEnabled(true);
        } catch (ApiException | RuntimeException e) {
            Toast.makeText(this, getString(R.string.error_google_sign_in_failed), Toast.LENGTH_LONG).show();
            refreshGoogleSignInState();
        }
    }

    private void signOutGoogle() {
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            if (currentAccount != null && "google".equals(currentAccount.getProvider())) {
                updateActiveAccount(localStorageAccountManager.useLocalStorage());
            }
            refreshGoogleSignInState();
            Toast.makeText(this, getString(R.string.google_drive_signed_out), Toast.LENGTH_SHORT).show();
        });
    }

    private void maybePromptForInitialLogin() {
        if (preferences.getBoolean(INITIAL_LOGIN_PROMPT_COMPLETED_KEY, false)) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.initial_login_dialog_title)
                .setMessage(R.string.initial_login_dialog_message)
                .setCancelable(false)
                .setPositiveButton(R.string.button_continue_with_google, (dialog, which) -> {
                    preferences.edit().putBoolean(INITIAL_LOGIN_PROMPT_COMPLETED_KEY, true).apply();
                    refreshGoogleSignInState();
                    showSection(googleDriveSection, getString(R.string.google_drive_title));
                    signInWithGoogle();
                })
                .setNegativeButton(R.string.button_continue_with_microsoft, (dialog, which) -> {
                    preferences.edit().putBoolean(INITIAL_LOGIN_PROMPT_COMPLETED_KEY, true).apply();
                    showSection(microsoftDriveSection, getString(R.string.microsoft_drive_title));
                })
                .show();
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
        microsoftClientIdField.setText(preferences.getString(MICROSOFT_CLIENT_ID_KEY, ""));
    }

    private void browseDataDirectory() {
        String typed = dataDirectoryLabel.getText().toString().trim();
        Path target;
        try {
            target = typed.isEmpty() ? dataDirectory : resolveUnderAppStorage(typed);
        } catch (IOException | RuntimeException e) {
            Toast.makeText(this, getString(R.string.select_folder_failed), Toast.LENGTH_LONG).show();
            dataDirectoryLabel.setText(dataDirectory.toAbsolutePath().normalize().toString());
            return;
        }
        try {
            Files.createDirectories(target);
            dataDirectory = target;
            dataDirectoryLabel.setText(target.toAbsolutePath().normalize().toString());
            String contents = listDataDirectoryContents();
            new AlertDialog.Builder(this)
                    .setTitle(R.string.select_folder_dialog_title)
                    .setMessage(getString(
                            R.string.select_folder_dialog_message,
                            target.toAbsolutePath().normalize(),
                            contents
                    ))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (IOException | RuntimeException e) {
            Toast.makeText(this, getString(R.string.select_folder_failed), Toast.LENGTH_LONG).show();
        }
    }

    private Path resolveUnderAppStorage(String typed) throws IOException {
        Path root = getFilesDir().toPath().toAbsolutePath().normalize();
        Path candidate = Path.of(typed).toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) {
            throw new IOException("Selected folder must be within the app storage directory.");
        }
        return candidate;
    }

    private String listDataDirectoryContents() throws IOException {
        try (Stream<Path> paths = Files.list(dataDirectory)) {
            List<String> items = paths
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .map(path -> Files.isDirectory(path)
                            ? getString(R.string.select_folder_item_directory, path.getFileName())
                            : getString(R.string.select_folder_item_file, path.getFileName()))
                    .collect(Collectors.toList());
            return items.isEmpty()
                    ? getString(R.string.select_folder_empty)
                    : String.join("\n", items);
        }
    }

    private void refreshProfiles() {
        profileItems.clear();
        List<ManagedProfile> profiles = profileManager.listProfiles(currentAccount.getAccountId());
        for (ManagedProfile profile : profiles) {
            profileItems.add(profile.getProfileName() + " (" + profile.getProfileType() + ")");
        }
        profileAdapter.notifyDataSetChanged();
    }

    private void refreshGoogleSignInState() {
        GoogleSignInAccount signedInAccount = GoogleSignIn.getLastSignedInAccount(this);
        boolean signedIn = signedInAccount != null && signedInAccount.getEmail() != null;
        signOutGoogleButton.setEnabled(signedIn);
        if (signedIn) {
            googleSetupStatus.setText(getString(
                    R.string.google_drive_available_as,
                    describeGoogleAccount(signedInAccount)
            ));
            return;
        }
        googleSetupStatus.setText(getString(R.string.google_drive_setup_default_status));
    }

    private void updateActiveAccount(GoogleAccount account) {
        currentAccount = account;
        accountLabel.setText(getString(R.string.signed_in_as, describeAccount(account)));
        refreshProfiles();
    }

    private String describeAccount(GoogleAccount account) {
        if (account.getEmail() == null || account.getEmail().isBlank()) {
            return account.getDisplayName();
        }
        if (account.getDisplayName() == null
                || account.getDisplayName().isBlank()
                || account.getDisplayName().equals(account.getEmail())) {
            return account.getEmail();
        }
        return account.getDisplayName() + " (" + account.getEmail() + ")";
    }

    private String describeGoogleAccount(GoogleSignInAccount account) {
        String email = account.getEmail();
        String displayName = account.getDisplayName();
        if (email == null || email.isBlank()) {
            return displayName == null || displayName.isBlank() ? getString(R.string.google_drive_title) : displayName;
        }
        if (displayName == null || displayName.isBlank() || displayName.equals(email)) {
            return email;
        }
        return displayName + " (" + email + ")";
    }
}
