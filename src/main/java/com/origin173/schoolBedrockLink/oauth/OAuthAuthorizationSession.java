package com.origin173.schoolBedrockLink.oauth;

import com.origin173.schoolBedrockLink.link.PendingLink;
import java.time.Instant;
import java.util.Objects;

public record OAuthAuthorizationSession(String state, String codeVerifier, PendingLink pending,
                                        Instant createdAt, Instant expiresAt,
                                        com.origin173.schoolBedrockLink.config.PluginConfig snapshot) {

    public OAuthAuthorizationSession(String state, String codeVerifier, PendingLink pending,
                                     Instant createdAt, Instant expiresAt) {
        this(state, codeVerifier, pending, createdAt, expiresAt, null);
    }

    public OAuthAuthorizationSession {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(codeVerifier, "codeVerifier");
        Objects.requireNonNull(pending, "pending");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean expired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
