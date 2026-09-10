package com.origin173.schoolBedrockLink.oauth;

import com.origin173.schoolBedrockLink.link.PendingLink;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ConfirmationSession(String token, PendingLink pending,
                                  List<MinecraftProfile> profiles,
                                  Instant createdAt, Instant expiresAt) {

    public ConfirmationSession {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(pending, "pending");
        profiles = List.copyOf(profiles);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean expired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
