package com.timemanagement.android.gui;

import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.ManagedProfile;
import com.timemanagement.core.program.GoogleLoginManager;
import com.timemanagement.core.program.ProfileManager;

import java.util.List;

public class AndroidGuiPlaceholder {
    private final GoogleLoginManager loginManager;
    private final ProfileManager profileManager;

    public AndroidGuiPlaceholder(GoogleLoginManager loginManager, ProfileManager profileManager) {
        this.loginManager = loginManager;
        this.profileManager = profileManager;
    }

    public GoogleAccount signInWithGoogle(String email, String displayName) {
        return loginManager.login(email, displayName);
    }

    public ManagedProfile createProfile(String accountId, String profileName, String profileType) {
        return profileManager.createProfile(accountId, profileName, profileType);
    }

    public List<ManagedProfile> listProfiles(String accountId) {
        return profileManager.listProfiles(accountId);
    }
}
