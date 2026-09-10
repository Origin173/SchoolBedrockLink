package com.origin173.schoolBedrockLink.floodgate;

import java.time.Duration;
import java.util.UUID;

/** Official linking operations used by the registry coordinator. */
public interface LinkOperations {
    boolean isReady();
    MappingLookup getMapping(UUID bedrockUuid, Duration timeout);
    boolean linkPlayer(UUID bedrockUuid, UUID javaUuid, String javaUsername, Duration timeout);
    boolean unlinkPlayer(UUID javaUuid, Duration timeout);
}
