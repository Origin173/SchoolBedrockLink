package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.floodgate.FloodgateMapping;
import com.origin173.schoolBedrockLink.link.ApprovedLink;
import com.origin173.schoolBedrockLink.link.LinkPolicy;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinkPolicyTest {

    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID JAVA_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID JAVA_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void globalOrExternalMappingCannotBypassSchoolOAuth() {
        FloodgateMapping externalMapping = new FloodgateMapping(JAVA_A, "SchoolUser");

        assertEquals(LinkPolicy.Decision.DENY_UNAPPROVED,
                LinkPolicy.decide(null, externalMapping, true, true));
    }

    @Test
    void approvedMappingAllowsAccess() {
        ApprovedLink approved = approved(JAVA_A);

        assertEquals(LinkPolicy.Decision.ALLOW,
                LinkPolicy.decide(approved, new FloodgateMapping(JAVA_A, "SchoolUser"), true, true));
    }

    @Test
    void mappingConflictIsDenied() {
        ApprovedLink approved = approved(JAVA_A);

        assertEquals(LinkPolicy.Decision.CONFLICT,
                LinkPolicy.decide(approved, new FloodgateMapping(JAVA_B, "Other"), true, true));
    }

    private static ApprovedLink approved(UUID javaUuid) {
        return new ApprovedLink(BEDROCK, "2533270000000001", "OriginBE", javaUuid,
                "SchoolUser", Instant.parse("2026-09-09T08:00:00Z"));
    }
}
