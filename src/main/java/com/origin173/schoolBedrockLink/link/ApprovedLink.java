package com.origin173.schoolBedrockLink.link;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ApprovedLink(UUID bedrockUuid, String xuid, String gamertag,
                           UUID javaUuid, String javaUsername, Instant linkedAt) {

    public ApprovedLink {
        Objects.requireNonNull(bedrockUuid, "bedrockUuid");
        Objects.requireNonNull(javaUuid, "javaUuid");
        Objects.requireNonNull(linkedAt, "linkedAt");
        if (xuid == null || xuid.isBlank() || xuid.length() > 128
                || gamertag == null || gamertag.isBlank() || gamertag.length() > 64
                || javaUsername == null || javaUsername.isBlank() || javaUsername.length() > 64) {
            throw new IllegalArgumentException("Approved link identity fields are required");
        }
        xuid = xuid.trim();
        gamertag = gamertag.trim();
        javaUsername = javaUsername.trim();
    }
}
