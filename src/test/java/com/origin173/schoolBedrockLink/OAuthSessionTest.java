package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.floodgate.BedrockIdentity;
import com.origin173.schoolBedrockLink.link.PendingLink;
import com.origin173.schoolBedrockLink.config.OAuthConfig;
import com.origin173.schoolBedrockLink.oauth.OAuthAuthorizationSession;
import com.origin173.schoolBedrockLink.oauth.OAuthService;
import com.origin173.schoolBedrockLink.oauth.OAuthSessionService;
import com.origin173.schoolBedrockLink.security.PkceUtil;
import com.origin173.schoolBedrockLink.security.SecureTokenGenerator;
import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthSessionTest {

    @Test
    void expiredAndRepeatedStateAreRejected() {
        Instant now = Instant.parse("2026-09-09T08:00:00Z");
        PendingLink pending = new PendingLink("7K3FP92AJQ",
                new BedrockIdentity(UUID.randomUUID(), "1234", "OriginBE"),
                now, now.plusSeconds(300));
        OAuthSessionService sessions = new OAuthSessionService(new SecureTokenGenerator());
        OAuthAuthorizationSession expired = sessions.createAuthorization(pending, now, Duration.ofSeconds(1));
        assertTrue(sessions.consumeState(expired.state(), now.plusSeconds(1)).isEmpty());

        OAuthAuthorizationSession oneTime = sessions.createAuthorization(pending, now, Duration.ofMinutes(5));
        assertTrue(sessions.consumeState(oneTime.state(), now).isPresent());
        assertTrue(sessions.consumeState(oneTime.state(), now).isEmpty());
    }

    @Test
    void pkceS256ChallengeMatchesRfc7636Vector() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                PkceUtil.challengeFor(verifier));
        assertFalse(verifier.isBlank());
    }

    @Test
    void emptyBlessingSkinScopesAreOmittedFromAuthorizationRequest() {
        PendingLink pending = new PendingLink("7K3FP92AJQ",
                new BedrockIdentity(UUID.randomUUID(), "1234", "OriginBE"),
                Instant.now(), Instant.now().plusSeconds(300));
        OAuthSessionService sessions = new OAuthSessionService(new SecureTokenGenerator());
        OAuthAuthorizationSession session = sessions.createAuthorization(pending, Instant.now(),
                Duration.ofMinutes(5));
        OAuthConfig config = new OAuthConfig("blessing-skin", URI.create("https://skin.example.test/oauth/authorize"),
                URI.create("https://skin.example.test/oauth/token"), URI.create("https://skin.example.test/api/user"),
                URI.create("https://skin.example.test/api/players"),
                URI.create("https://skin.example.test/api/yggdrasil/api/profiles/minecraft"),
                "client", "secret", List.of(), true);

        String location = new OAuthService(config, Duration.ofSeconds(1))
                .authorizationUrl(session, "https://auth.example.test/oauth/callback");

        assertFalse(location.contains("scope="));
    }
}
