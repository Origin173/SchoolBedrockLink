package com.origin173.schoolBedrockLink.link;

import com.origin173.schoolBedrockLink.floodgate.FloodgateMapping;

/** Pure access decision used both by the runtime service and focused tests. */
public final class LinkPolicy {

    private LinkPolicy() {
    }

    public static Decision decide(ApprovedLink approved, FloodgateMapping currentMapping,
                                  boolean mappingLookupAvailable, boolean autoRepair) {
        if (approved == null) {
            return Decision.DENY_UNAPPROVED;
        }
        if (!mappingLookupAvailable) {
            return Decision.DENY_NOT_READY;
        }
        if (currentMapping == null) {
            return autoRepair ? Decision.REPAIR_REQUIRED : Decision.DENY_NOT_READY;
        }
        if (!approved.javaUuid().equals(currentMapping.javaUuid())) {
            return Decision.CONFLICT;
        }
        return Decision.ALLOW;
    }

    public enum Decision {
        ALLOW,
        DENY_UNAPPROVED,
        DENY_NOT_READY,
        REPAIR_REQUIRED,
        CONFLICT
    }
}
