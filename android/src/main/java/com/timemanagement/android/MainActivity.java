package com.timemanagement.android;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.timemanagement.core.data.JsonDataStore;
import com.timemanagement.core.dataclass.ManagedProfile;
import com.timemanagement.core.program.ProfileManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ProfileManager profileManager;
    private String currentAccountId;

    private EditText accountIdField;
    private EditText profileNameField;
    private EditText profileTypeField;
    private Button addProfileButton;
    private TextView accountLabel;
    private ListView profileListView;
    private ArrayAdapter<String> profileAdapter;
    private final List<String> profileItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Path dataDir = getFilesDir().toPath().resolve("data");
        JsonDataStore dataStore = new JsonDataStore(dataDir);
        profileManager = new ProfileManager(dataStore);

        accountIdField = findViewById(R.id.accountIdField);
        profileNameField = findViewById(R.id.profileNameField);
        profileTypeField = findViewById(R.id.profileTypeField);
        addProfileButton = findViewById(R.id.addProfileButton);
        accountLabel = findViewById(R.id.accountLabel);
        profileListView = findViewById(R.id.profileListView);

        profileAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, profileItems);
        profileListView.setAdapter(profileAdapter);

        Button setAccountButton = findViewById(R.id.setAccountButton);
        setAccountButton.setOnClickListener(v -> setAccount());
        addProfileButton.setOnClickListener(v -> addProfile());
    }

    private void setAccount() {
        String accountId = accountIdField.getText().toString().trim();
        if (accountId.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_account_id_required), Toast.LENGTH_SHORT).show();
            return;
        }
        currentAccountId = accountId;
        accountLabel.setText(getString(R.string.signed_in_as, accountId));
        addProfileButton.setEnabled(true);
        refreshProfiles();
    }

    private void addProfile() {
        if (currentAccountId == null) {
            return;
        }
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

    private void refreshProfiles() {
        profileItems.clear();
        if (currentAccountId == null) {
            profileAdapter.notifyDataSetChanged();
            return;
        }
        List<ManagedProfile> profiles = profileManager.listProfiles(currentAccountId);
        for (ManagedProfile profile : profiles) {
            profileItems.add(profile.getProfileName() + " (" + profile.getProfileType() + ")");
        }
        profileAdapter.notifyDataSetChanged();
    }
}
