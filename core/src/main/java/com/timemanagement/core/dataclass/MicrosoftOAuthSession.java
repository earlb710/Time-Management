package com.timemanagement.core.dataclass;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MicrosoftOAuthSession {
    private MicrosoftIdentity identity;
    private String accessToken;
    private String refreshToken;
    private Instant expiresAt;
    private List<String> scopes;

    public MicrosoftOAuthSession() {
    }

    public MicrosoftOAuthSession(MicrosoftIdentity identity, String accessToken, String refreshToken, Instant expiresAt, List<String> scopes) {
        this.identity = identity;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    public MicrosoftIdentity getIdentity() {
        return identity;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public List<String> getScopes() {
        return scopes == null ? List.of() : new ArrayList<>(scopes);
    }

    public boolean requiresRefresh(Clock clock) {
        if (expiresAt == null) {
            return true;
        }
        return !expiresAt.isAfter(clock.instant().plusSeconds(60));
    }
}
