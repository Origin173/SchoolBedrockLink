package com.origin173.schoolBedrockLink.floodgate;

import java.util.Objects;
import java.util.UUID;

public record BedrockIdentity(UUID bedrockUuid, String xuid, String gamertag) {

    public BedrockIdentity {
        Objects.requireNonNull(bedrockUuid, "bedrockUuid");
        if (xuid == null || xuid.isBlank() || xuid.length() > 128) {
            throw new IllegalArgumentException("xuid is required");
        }
        if (gamertag == null || gamertag.isBlank() || gamertag.length() > 64) {
            throw new IllegalArgumentException("gamertag is required");
        }
        xuid = xuid.trim();
        gamertag = gamertag.trim();
    }
}
