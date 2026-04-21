package com.timemanagement.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
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
import com.timemanagement.core.dataclass.MicrosoftIdentity;
import com.timemanagement.core.program.GoogleLoginManager;
import com.timemanagement.core.program.LocalStorageAccountManager;
import com.timemanagement.core.program.MicrosoftLoginManager;
import com.timemanagement.core.program.ProfileManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {
    private static final String STORAGE_PREFS = "storage-setup";
    private static final String INITIAL_LOGIN_PROMPT_COMPLETED_KEY = "initial-login-prompt-completed";
    private static final String GOOGLE_SERVER_CLIENT_ID_KEY = "google-server-client-id";
    private static final String GOOGLE_BACKEND_URL_KEY = "google-backend-url";
    private static final String MICROSOFT_CLIENT_ID_KEY = "microsoft-client-id";

    private GoogleLoginManager googleLoginManager;
    private MicrosoftLoginManager microsoftLoginManager;
    private ProfileManager profileManager;
    private LocalStorageAccountManager localStorageAccountManager;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private SharedPreferences preferences;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private Path dataDirectory;
    private GoogleAccount currentAccount;

    private TextView storageStatusLabel;
    private TextView accountLabel;
    private EditText dataDirectoryLabel;
    private EditText profileNameField;
    private EditText profileTypeField;
    private ListView profileListView;
    private TextView googleSetupStatus;
    private EditText googleServerClientIdField;
    private EditText googleBackendUrlField;
    private TextView microsoftSetupStatus;
    private EditText microsoftClientIdField;
    private EditText microsoftPassphraseField;
    private Button signInGoogleButton;
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
        microsoftLoginManager = new MicrosoftLoginManager(dataStore);
        profileManager = new ProfileManager(dataStore);
        localStorageAccountManager = new LocalStorageAccountManager(dataStore);
        preferences = getSharedPreferences(STORAGE_PREFS, MODE_PRIVATE);

        storageStatusLabel = findViewById(R.id.storageStatusLabel);
        accountLabel = findViewById(R.id.accountLabel);
        dataDirectoryLabel = findViewById(R.id.dataDirectoryLabel);
        profileNameField = findViewById(R.id.profileNameField);
        profileTypeField = findViewById(R.id.profileTypeField);
        profileListView = findViewById(R.id.profileListView);
        googleSetupStatus = findViewById(R.id.googleSetupStatus);
        googleServerClientIdField = findViewById(R.id.googleServerClientIdField);
        googleBackendUrlField = findViewById(R.id.googleBackendUrlField);
        microsoftSetupStatus = findViewById(R.id.microsoftSetupStatus);
        microsoftClientIdField = findViewById(R.id.microsoftClientIdField);
        microsoftPassphraseField = findViewById(R.id.microsoftPassphraseField);
        signInGoogleButton = findViewById(R.id.signInWithGoogleButton);
        signOutGoogleButton = findViewById(R.id.signOutGoogleButton);
        localStorageSection = findViewById(R.id.localStorageSection);
        googleDriveSection = findViewById(R.id.googleDriveSection);
        microsoftDriveSection = findViewById(R.id.microsoftDriveSection);

        profileAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, profileItems);
        profileListView.setAdapter(profileAdapter);

        updateActiveAccount(localStorageAccountManager.useLocalStorage());
        loadSavedSetup();
        rebuildGoogleSignInClient();
        refreshGoogleSignInState();

        Button addProfileButton = findViewById(R.id.addProfileButton);
        addProfileButton.setOnClickListener(v -> addProfile());
        findViewById(R.id.browseDataDirectoryButton).setOnClickListener(v -> browseDataDirectory());
        findViewById(R.id.saveGoogleSetupButton).setOnClickListener(v -> saveGoogleSetup());
        findViewById(R.id.clearGoogleSetupButton).setOnClickListener(v -> clearGoogleSetup());
        signInGoogleButton.setOnClickListener(v -> signInWithGoogle());
        signOutGoogleButton.setOnClickListener(v -> signOutGoogle());
        findViewById(R.id.saveMicrosoftSetupButton).setOnClickListener(v -> saveMicrosoftSetup());
        findViewById(R.id.clearMicrosoftSetupButton).setOnClickListener(v -> clearMicrosoftSetup());

        dataDirectoryLabel.setText(dataDirectory.toAbsolutePath().normalize().toString());
        showSection(localStorageSection, getString(R.string.local_storage_default_status));
        maybePromptForRequiredLogin();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkExecutor.shutdownNow();
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
        if (!saveGoogleSetupInternal(true)) {
            showSection(googleDriveSection, getString(R.string.google_drive_title));
            return;
        }
        signInGoogleButton.setEnabled(false);
        googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
    }

    private void handleGoogleSignInResult(Intent data) {
        try {
            GoogleSignInAccount signedInAccount = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException.class);
            if (signedInAccount == null || signedInAccount.getId() == null || signedInAccount.getEmail() == null) {
                throw new IllegalStateException(getString(R.string.error_google_sign_in_failed));
            }
            GoogleVerificationConfig verificationConfig = requireGoogleVerificationConfig();
            String idToken = signedInAccount.getIdToken();
            if (idToken == null || idToken.isBlank()) {
                throw new IllegalStateException(getString(R.string.error_google_id_token_missing));
            }
            googleSetupStatus.setText(getString(R.string.google_drive_verifying_status));
            verifyGoogleSignInOnBackend(signedInAccount, idToken, verificationConfig);
        } catch (ApiException | RuntimeException e) {
            signInGoogleButton.setEnabled(true);
            Toast.makeText(this, getString(R.string.error_google_sign_in_failed), Toast.LENGTH_LONG).show();
            refreshGoogleSignInState();
        }
    }

    private void signOutGoogle() {
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            signInGoogleButton.setEnabled(true);
            refreshGoogleSignInState();
            Toast.makeText(this, getString(R.string.google_drive_signed_out), Toast.LENGTH_SHORT).show();
        });
    }

    private void maybePromptForRequiredLogin() {
        if (!requiresStartupLogin()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.startup_login_dialog_title)
                .setMessage(R.string.startup_login_dialog_message)
                .setCancelable(false)
                .setPositiveButton(R.string.button_login_google, (dialog, which) -> promptForGoogleEmailLogin())
                .setNegativeButton(R.string.button_login_microsoft, (dialog, which) -> promptForMicrosoftEmailLogin())
                .setNeutralButton(android.R.string.cancel, (dialog, which) -> finish())
                .show();
    }

    private void promptForGoogleEmailLogin() {
        EditText emailField = new EditText(this);
        emailField.setHint(R.string.hint_login_email);
        emailField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        new AlertDialog.Builder(this)
                .setTitle(R.string.google_login_dialog_title)
                .setMessage(R.string.google_login_dialog_message)
                .setView(emailField)
                .setCancelable(false)
                .setPositiveButton(R.string.button_sign_in, (dialog, which) -> {
                    String email = normalizeLoginEmail(emailField.getText().toString());
                    if (email == null) {
                        Toast.makeText(this, getString(R.string.error_login_email_required), Toast.LENGTH_LONG).show();
                        promptForGoogleEmailLogin();
                        return;
                    }
                    GoogleAccount account = googleLoginManager.login(new GoogleIdentity(email, email, email));
                    completeStartupLogin(account);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .show();
    }

    private void promptForMicrosoftEmailLogin() {
        EditText emailField = new EditText(this);
        emailField.setHint(R.string.hint_login_email);
        emailField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        new AlertDialog.Builder(this)
                .setTitle(R.string.microsoft_login_dialog_title)
                .setMessage(R.string.microsoft_login_dialog_message)
                .setView(emailField)
                .setCancelable(false)
                .setPositiveButton(R.string.button_sign_in, (dialog, which) -> {
                    String email = normalizeLoginEmail(emailField.getText().toString());
                    if (email == null) {
                        Toast.makeText(this, getString(R.string.error_login_email_required), Toast.LENGTH_LONG).show();
                        promptForMicrosoftEmailLogin();
                        return;
                    }
                    GoogleAccount account = microsoftLoginManager.login(new MicrosoftIdentity(email, email, email));
                    completeStartupLogin(account);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .show();
    }

    private String normalizeLoginEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase();
        return Patterns.EMAIL_ADDRESS.matcher(email).matches() ? email : null;
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
        googleServerClientIdField.setText(preferences.getString(GOOGLE_SERVER_CLIENT_ID_KEY, ""));
        googleBackendUrlField.setText(preferences.getString(GOOGLE_BACKEND_URL_KEY, ""));
        microsoftClientIdField.setText(preferences.getString(MICROSOFT_CLIENT_ID_KEY, ""));
    }

    private void saveGoogleSetup() {
        if (saveGoogleSetupInternal(true)) {
            Toast.makeText(this, getString(R.string.google_drive_setup_saved), Toast.LENGTH_LONG).show();
        }
    }

    private boolean saveGoogleSetupInternal(boolean showErrors) {
        try {
            GoogleVerificationConfig verificationConfig = buildGoogleVerificationConfigFromInputs();
            preferences.edit()
                    .putString(GOOGLE_SERVER_CLIENT_ID_KEY, verificationConfig.getServerClientId())
                    .putString(GOOGLE_BACKEND_URL_KEY, verificationConfig.getBackendVerificationUrl())
                    .apply();
            rebuildGoogleSignInClient();
            googleSetupStatus.setText(getString(R.string.google_drive_setup_saved));
            return true;
        } catch (IllegalArgumentException e) {
            if (showErrors) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                googleSetupStatus.setText(e.getMessage());
            }
            return false;
        }
    }

    private void clearGoogleSetup() {
        preferences.edit()
                .remove(GOOGLE_SERVER_CLIENT_ID_KEY)
                .remove(GOOGLE_BACKEND_URL_KEY)
                .apply();
        googleServerClientIdField.setText("");
        googleBackendUrlField.setText("");
        rebuildGoogleSignInClient();
        signInGoogleButton.setEnabled(true);
        googleSetupStatus.setText(getString(R.string.google_drive_setup_optional_status));
        refreshGoogleSignInState();
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
        if (!hasSavedGoogleVerificationConfig()) {
            googleSetupStatus.setText(getString(R.string.google_drive_setup_optional_status));
            return;
        }
        googleSetupStatus.setText(getString(R.string.google_drive_setup_default_status));
    }

    private void completeStartupLogin(GoogleAccount account) {
        updateActiveAccount(account);
        showSection(localStorageSection, getString(R.string.local_storage_default_status));
    }

    private void updateActiveAccount(GoogleAccount account) {
        currentAccount = account;
        preferences.edit()
                .putBoolean(INITIAL_LOGIN_PROMPT_COMPLETED_KEY, !isLoginRequired(account))
                .apply();
        accountLabel.setText(getString(R.string.signed_in_as, describeAccount(account)));
        refreshProfiles();
    }

    private boolean requiresStartupLogin() {
        return isLoginRequired(currentAccount);
    }

    private boolean isLoginRequired(GoogleAccount account) {
        if (account == null) {
            return true;
        }
        String provider = account.getProvider();
        return provider == null || provider.isBlank() || LocalStorageAccountManager.PROVIDER.equalsIgnoreCase(provider);
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

    private void rebuildGoogleSignInClient() {
        GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail();
        String serverClientId = preferences.getString(GOOGLE_SERVER_CLIENT_ID_KEY, "");
        if (serverClientId != null && !serverClientId.isBlank()) {
            builder.requestIdToken(serverClientId.trim());
        }
        googleSignInClient = GoogleSignIn.getClient(this, builder.build());
    }

    private boolean hasSavedGoogleVerificationConfig() {
        String savedServerClientId = preferences.getString(GOOGLE_SERVER_CLIENT_ID_KEY, "");
        String savedBackendUrl = preferences.getString(GOOGLE_BACKEND_URL_KEY, "");
        return savedServerClientId != null
                && !savedServerClientId.isBlank()
                && savedBackendUrl != null
                && !savedBackendUrl.isBlank();
    }

    private GoogleVerificationConfig requireGoogleVerificationConfig() {
        String serverClientId = preferences.getString(GOOGLE_SERVER_CLIENT_ID_KEY, "");
        String backendUrl = preferences.getString(GOOGLE_BACKEND_URL_KEY, "");
        if (serverClientId == null || serverClientId.isBlank() || backendUrl == null || backendUrl.isBlank()) {
            throw new IllegalStateException(getString(R.string.google_drive_setup_required_status));
        }
        return new GoogleVerificationConfig(serverClientId.trim(), normalizeHttpsUrl(backendUrl));
    }

    private GoogleVerificationConfig buildGoogleVerificationConfigFromInputs() {
        String serverClientId = googleServerClientIdField.getText().toString().trim();
        if (serverClientId.isEmpty()) {
            throw new IllegalArgumentException(getString(R.string.error_google_server_client_id_required));
        }
        return new GoogleVerificationConfig(serverClientId, normalizeHttpsUrl(googleBackendUrlField.getText().toString()));
    }

    private String normalizeHttpsUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(getString(R.string.error_google_backend_url_required));
        }
        try {
            URL url = new URL(normalized);
            if (!Objects.equals("https", url.getProtocol())) {
                throw new IllegalArgumentException(getString(R.string.error_google_backend_url_https_required));
            }
            return url.toString();
        } catch (IOException e) {
            throw new IllegalArgumentException(getString(R.string.error_google_backend_url_invalid), e);
        }
    }

    private void verifyGoogleSignInOnBackend(
            GoogleSignInAccount signedInAccount,
            String idToken,
            GoogleVerificationConfig verificationConfig
    ) {
        networkExecutor.execute(() -> {
            try {
                postGoogleIdToken(verificationConfig.getBackendVerificationUrl(), idToken, signedInAccount);
                runOnUiThread(() -> finishVerifiedGoogleSignIn(signedInAccount));
            } catch (RuntimeException e) {
                googleSignInClient.signOut();
                runOnUiThread(() -> {
                    signInGoogleButton.setEnabled(true);
                    googleSetupStatus.setText(e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    refreshGoogleSignInState();
                });
            }
        });
    }

    private void finishVerifiedGoogleSignIn(GoogleSignInAccount account) {
        googleSetupStatus.setText(getString(R.string.google_drive_connected_as, describeGoogleAccount(account)));
        signInGoogleButton.setEnabled(true);
        signOutGoogleButton.setEnabled(true);
    }

    private void postGoogleIdToken(String backendUrl, String idToken, GoogleSignInAccount account) {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(backendUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] body = buildGoogleVerificationPayload(idToken, account).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
            }
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException(readBackendError(connection, statusCode));
            }
        } catch (IOException | JSONException e) {
            throw new IllegalStateException(getString(R.string.error_google_backend_verification_failed), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JSONObject buildGoogleVerificationPayload(String idToken, GoogleSignInAccount account) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("idToken", idToken);
        payload.put("email", account.getEmail());
        payload.put("displayName", account.getDisplayName());
        payload.put("subjectId", account.getId());
        return payload;
    }

    private String readBackendError(HttpsURLConnection connection, int statusCode) {
        String body = readConnectionBody(connection.getErrorStream());
        if (body.isBlank()) {
            return getString(R.string.error_google_backend_verification_failed_with_status, statusCode);
        }
        return getString(R.string.error_google_backend_verification_failed_with_body, statusCode, body);
    }

    private String readConnectionBody(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(line);
            }
            return body.toString().trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static final class GoogleVerificationConfig {
        private final String serverClientId;
        private final String backendVerificationUrl;

        private GoogleVerificationConfig(String serverClientId, String backendVerificationUrl) {
            this.serverClientId = serverClientId;
            this.backendVerificationUrl = backendVerificationUrl;
        }

        private String getServerClientId() {
            return serverClientId;
        }

        private String getBackendVerificationUrl() {
            return backendVerificationUrl;
        }
    }
}
