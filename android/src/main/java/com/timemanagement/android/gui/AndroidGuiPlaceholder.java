package com.timemanagement.android.gui;

import com.timemanagement.core.dataclass.GoogleAccount;
import com.timemanagement.core.dataclass.GoogleIdentity;
import com.timemanagement.core.dataclass.GoogleOAuthSession;
import com.timemanagement.core.dataclass.ManagedProfile;
import com.timemanagement.core.dataclass.MicrosoftIdentity;
import com.timemanagement.core.dataclass.MicrosoftOAuthSession;
import com.timemanagement.core.program.GoogleLoginManager;
import com.timemanagement.core.program.MicrosoftLoginManager;
import com.timemanagement.core.program.ProfileManager;

import java.util.List;

public class AndroidGuiPlaceholder {
    private final GoogleLoginManager loginManager;
    private final MicrosoftLoginManager microsoftLoginManager;
    private final ProfileManager profileManager;

    public AndroidGuiPlaceholder(GoogleLoginManager loginManager, MicrosoftLoginManager microsoftLoginManager, ProfileManager profileManager) {
        this.loginManager = loginManager;
        this.microsoftLoginManager = microsoftLoginManager;
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

    public GoogleAccount signInWithMicrosoft(MicrosoftIdentity identity) {
        return microsoftLoginManager.login(identity);
    }

    public GoogleAccount restoreMicrosoftSession(MicrosoftOAuthSession session) {
        if (session == null) {
            throw new IllegalArgumentException("OAuth session is required.");
        }
        return microsoftLoginManager.login(session.getIdentity());
    }

    public ManagedProfile createProfile(String accountId, String profileName, String profileType) {
        return profileManager.createProfile(accountId, profileName, profileType);
    }

    public List<ManagedProfile> listProfiles(String accountId) {
        return profileManager.listProfiles(accountId);
    }
}
