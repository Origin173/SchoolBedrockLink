package com.origin173.schoolBedrockLink.oauth;

import java.util.Objects;
import java.util.UUID;

public record MinecraftProfile(UUID uuid, String username) {

    public MinecraftProfile {
        Objects.requireNonNull(uuid, "uuid");
        if (username == null || username.isBlank() || username.length() > 64) {
            throw new IllegalArgumentException("Invalid Minecraft profile username");
        }
        username = username.trim();
    }
}
