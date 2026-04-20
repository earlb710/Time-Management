package com.timemanagement.android.gui;

import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.GoogleIdentity;
import com.timemanagement.core.dataclass.GoogleOAuthSession;
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

    public GoogleAccount signInWithGoogle(GoogleIdentity identity) {
        return loginManager.login(identity);
    }

    public GoogleAccount restoreGoogleSession(GoogleOAuthSession session) {
        if (session == null) {
            throw new IllegalArgumentException("OAuth session is required.");
        }
        return loginManager.login(session.getIdentity());
    }

    public ManagedProfile createProfile(String accountId, String profileName, String profileType) {
        return profileManager.createProfile(accountId, profileName, profileType);
    }

    public List<ManagedProfile> listProfiles(String accountId) {
        return profileManager.listProfiles(accountId);
    }
}
