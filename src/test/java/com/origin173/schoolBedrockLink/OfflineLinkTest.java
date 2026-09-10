package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.audit.AuditLogger;
import com.origin173.schoolBedrockLink.floodgate.*;
import com.origin173.schoolBedrockLink.link.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OfflineLinkTest {
    @TempDir Path directory;
    @Test
    void offlineStableIdentifiersUnlinkOnlyVerifiedMappingAndAuditSuccess() throws Exception {
        var logger = Logger.getAnonymousLogger();
        var registry = new ApprovedLinkRegistry(directory, logger);
        var approved = new ApprovedLink(UUID.randomUUID(), "1234567890123456", "Bedrock",
                UUID.randomUUID(), "JavaPlayer", Instant.now());
        assertTrue(registry.save(approved));
        assertEquals(approved, registry.resolveIdentifier("java:" + approved.javaUuid()));
        assertEquals(approved, registry.resolveIdentifier("bedrock:" + approved.bedrockUuid()));
        assertEquals(approved, registry.resolveIdentifier("xuid:" + approved.xuid()));
        var fake = new FakeLinks();
        var links = new LinkService(registry, fake, new AuditLogger(directory, logger), logger, Duration.ofSeconds(1));
        fake.mapping = new FloodgateMapping(UUID.randomUUID(), "Wrong");
        assertEquals(LinkService.UnlinkStatus.CONFLICT, links.unlink(approved).status());
        assertEquals(0, fake.unlinkCalls);
        assertNotNull(registry.findByBedrock(approved.bedrockUuid()));
        fake.mapping = new FloodgateMapping(approved.javaUuid(), approved.javaUsername());
        fake.ready = false;
        assertEquals(LinkService.UnlinkStatus.NOT_READY, links.unlink(approved).status());
        fake.ready = true;
        fake.unlinkWorks = false;
        assertEquals(LinkService.UnlinkStatus.FAILED, links.unlink(approved).status());
        assertNotNull(registry.findByBedrock(approved.bedrockUuid()));
        fake.unlinkWorks = true;
        assertEquals(LinkService.UnlinkStatus.SUCCESS, links.unlink(approved).status());
        assertNull(registry.findByBedrock(approved.bedrockUuid()));
        String audit = Files.readString(directory.resolve("link-audit.jsonl"));
        assertTrue(audit.contains("UNLINK"));
        assertTrue(audit.contains("SUCCESS"));
        assertFalse(audit.contains(approved.xuid()));
        int calls = fake.unlinkCalls;
        assertEquals(LinkService.UnlinkStatus.CONFLICT, links.unlink(approved).status());
        assertEquals(calls, fake.unlinkCalls);
    }

    private static class FakeLinks implements LinkOperations {
        FloodgateMapping mapping;
        boolean ready = true;
        boolean unlinkWorks = true;
        int unlinkCalls;
        public boolean isReady() { return ready; }
        public MappingLookup getMapping(UUID uuid, Duration timeout) { return MappingLookup.available(mapping); }
        public boolean linkPlayer(UUID bedrock, UUID java, String name, Duration timeout) { return false; }
        public boolean unlinkPlayer(UUID java, Duration timeout) {
            unlinkCalls++;
            if (unlinkWorks) mapping = null;
            return unlinkWorks;
        }
    }
}
