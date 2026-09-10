package com.origin173.schoolBedrockLink.floodgate;

import java.util.Objects;
import java.util.UUID;

public record FloodgateMapping(UUID javaUuid, String javaUsername) {

    public FloodgateMapping {
        Objects.requireNonNull(javaUuid, "javaUuid");
        if (javaUsername == null || javaUsername.isBlank()) {
            javaUsername = "unknown";
        } else {
            javaUsername = javaUsername.trim();
        }
    }
}
