package com.origin173.schoolBedrockLink.link;

import com.origin173.schoolBedrockLink.floodgate.BedrockIdentity;
import java.time.Instant;
import java.util.Objects;

public record PendingLink(String bindingCode, BedrockIdentity identity,
                          Instant createdAt, Instant expiresAt) {

    public PendingLink {
        Objects.requireNonNull(bindingCode, "bindingCode");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean expired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
