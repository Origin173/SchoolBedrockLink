package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.floodgate.BedrockIdentity;
import com.origin173.schoolBedrockLink.link.PendingLink;
import com.origin173.schoolBedrockLink.link.PendingLinkService;
import com.origin173.schoolBedrockLink.security.SecureTokenGenerator;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingLinkServiceTest {

    @Test
    void threeHundredSecondsExpiresAtOriginalDeadlineEvenWhenReused() {
        var service = new PendingLinkService(new SecureTokenGenerator());
        var identity = new BedrockIdentity(UUID.randomUUID(), "1234", "Fixture");
        var now = Instant.parse("2026-09-09T08:00:00Z");
        var first = service.getOrCreate(identity, 10, 300, true, now);
        assertTrue(service.find(first.bindingCode(), now.plusSeconds(59)).isPresent());
        var reused = service.getOrCreate(identity, 10, 300, true, now.plusSeconds(250));
        org.junit.jupiter.api.Assertions.assertEquals(first.expiresAt(), reused.expiresAt());
        assertTrue(service.find(first.bindingCode(), now.plusSeconds(299)).isPresent());
        assertFalse(service.find(first.bindingCode(), now.plusSeconds(300)).isPresent());
    }

    @Test
    void clearInvalidatesActiveCodes() {
        PendingLinkService service = new PendingLinkService(new SecureTokenGenerator());
        Instant now = Instant.parse("2026-09-09T08:00:00Z");
        PendingLink link = service.getOrCreate(
                new BedrockIdentity(UUID.randomUUID(), "2533270000000001", "OriginBE"),
                10, 900, true, now);

        assertTrue(service.find(link.bindingCode(), now).isPresent());

        service.clear();

        assertFalse(service.find(link.bindingCode(), now).isPresent());
    }

    @Test
    void generatedCodeRoundTripsThroughNormalization() {
        PendingLinkService service = new PendingLinkService(new SecureTokenGenerator());
        Instant now = Instant.parse("2026-09-09T08:00:00Z");
        PendingLink link = service.getOrCreate(
                new BedrockIdentity(UUID.randomUUID(), "2533270000000002", "OriginBE2"),
                10, 300, true, now);

        assertEquals(link.bindingCode(), PendingLinkService.normalize(link.bindingCode()));
        assertTrue(service.find(link.bindingCode().toLowerCase(Locale.ROOT), now).isPresent());
    }

    @Test
    void typedCodeToleratesSeparatorsAndCrockfordConfusables() {
        assertEquals("B7K2QX9M3P", PendingLinkService.normalize(" b7k2-qx9m-3p "));
        assertEquals("B7K2QX9M3P", PendingLinkService.normalize("B7K2 QX9M 3P"));
        // I, L and O never occur in a generated code, so folding them cannot collide.
        assertEquals("B7K2QX9M01", PendingLinkService.normalize("B7K2QX9MOL"));

        assertNull(PendingLinkService.normalize("B7K2QX9"));
        assertNull(PendingLinkService.normalize("B7K2QX9M3PU"));
        assertNull(PendingLinkService.normalize("B7K2QX9M3!"));
    }
}
