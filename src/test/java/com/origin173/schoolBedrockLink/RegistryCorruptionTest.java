package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.link.ApprovedLinkRegistry;
import com.origin173.schoolBedrockLink.link.ApprovedLink;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryCorruptionTest {

    @TempDir
    Path tempDirectory;

    @Test
    void corruptRegistryFailsClosedWithoutReplacingTheOriginal() throws Exception {
        Path file = tempDirectory.resolve("approved-links.json");
        String corrupt = "{ this is not a registry }";
        Files.writeString(file, corrupt, StandardCharsets.UTF_8);

        ApprovedLinkRegistry registry = new ApprovedLinkRegistry(tempDirectory, Logger.getAnonymousLogger());

        assertFalse(registry.isHealthy());
        assertEquals(corrupt, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void validUpdatesKeepARecoverableBackup() throws Exception {
        ApprovedLinkRegistry registry = new ApprovedLinkRegistry(tempDirectory, Logger.getAnonymousLogger());
        ApprovedLink link = new ApprovedLink(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "2533270000000001", "OriginBE",
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "SchoolUser",
                Instant.parse("2026-09-09T08:00:00Z"));

        assertTrue(registry.save(link));
        // The constructor creates the initial empty registry, so the first update
        // already has a previous version to preserve.
        assertTrue(Files.exists(tempDirectory.resolve("approved-links.json.bak")));
        assertEquals(1, registry.snapshot().size());
        ApprovedLink updated = new ApprovedLink(link.bedrockUuid(), link.xuid(), "OriginBE-Updated",
                link.javaUuid(), link.javaUsername(), link.linkedAt());
        assertTrue(registry.save(updated));
        assertTrue(Files.exists(tempDirectory.resolve("approved-links.json.bak")));
        assertEquals("OriginBE", new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(Files.readString(tempDirectory.resolve("approved-links.json.bak")))
                .path("links").get(0).path("gamertag").asText());
    }
}
