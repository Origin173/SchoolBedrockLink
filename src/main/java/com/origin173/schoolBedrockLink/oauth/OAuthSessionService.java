package com.origin173.schoolBedrockLink.oauth;

import com.origin173.schoolBedrockLink.link.PendingLink;
import com.origin173.schoolBedrockLink.security.SecureTokenGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Owns one-time OAuth state and confirmation sessions; no token is persisted. */
public final class OAuthSessionService {

    private final SecureTokenGenerator tokens;
    private final ConcurrentHashMap<String, OAuthAuthorizationSession> oauthSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConfirmationSession> confirmations = new ConcurrentHashMap<>();

    public OAuthSessionService(SecureTokenGenerator tokens) {
        this.tokens = tokens;
    }

    public OAuthAuthorizationSession createAuthorization(PendingLink pending, Instant now, Duration lifetime) {
        return createAuthorization(pending, now, lifetime, null);
    }

    public OAuthAuthorizationSession createAuthorization(PendingLink pending, Instant now, Duration lifetime,
            com.origin173.schoolBedrockLink.config.PluginConfig snapshot) {
        OAuthAuthorizationSession session;
        do {
            String state = tokens.base64Url(32);
            String verifier = tokens.base64Url(32);
            session = new OAuthAuthorizationSession(state, verifier, pending, now, now.plus(lifetime), snapshot);
        } while (oauthSessions.putIfAbsent(session.state(), session) != null);
        return session;
    }

    /** Atomically consumes state, including when callback processing later fails. */
    public Optional<OAuthAuthorizationSession> consumeState(String state, Instant now) {
        if (state == null || state.isBlank() || state.length() > 256) {
            return Optional.empty();
        }
        OAuthAuthorizationSession session = oauthSessions.remove(state);
        if (session == null || session.expired(now)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public ConfirmationSession createConfirmation(PendingLink pending, java.util.List<MinecraftProfile> profiles,
                                                  Instant now, Duration lifetime) {
        ConfirmationSession session;
        do {
            session = new ConfirmationSession(tokens.base64Url(32), pending, profiles,
                    now, now.plus(lifetime));
        } while (confirmations.putIfAbsent(session.token(), session) != null);
        return session;
    }

    public Optional<ConfirmationSession> findConfirmation(String token, Instant now) {
        if (token == null || token.isBlank() || token.length() > 256) {
            return Optional.empty();
        }
        ConfirmationSession session = confirmations.get(token);
        if (session == null || session.expired(now)) {
            if (session != null) {
                confirmations.remove(token, session);
            }
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /** Atomically consumes a valid confirmation token to prevent double binding. */
    public Optional<ConfirmationSession> consumeConfirmation(String token, Instant now) {
        if (token == null || token.isBlank() || token.length() > 256) {
            return Optional.empty();
        }
        ConfirmationSession session = confirmations.remove(token);
        if (session == null || session.expired(now)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void cleanup(Instant now) {
        oauthSessions.entrySet().removeIf(entry -> entry.getValue().expired(now));
        confirmations.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }

    public int oauthSessionCount() {
        return oauthSessions.size();
    }

    public int confirmationCount() {
        return confirmations.size();
    }

    public void clear() {
        oauthSessions.clear();
        confirmations.clear();
    }
}
