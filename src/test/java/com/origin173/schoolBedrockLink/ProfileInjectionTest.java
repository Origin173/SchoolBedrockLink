package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.floodgate.BedrockIdentity;
import com.origin173.schoolBedrockLink.link.PendingLink;
import com.origin173.schoolBedrockLink.oauth.ConfirmationSession;
import com.origin173.schoolBedrockLink.oauth.MinecraftProfile;
import com.origin173.schoolBedrockLink.oauth.ProfileSelection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileInjectionTest {

    @Test
    void browserCanSubmitOnlyAnIndexFromTheOAuthProfileList() throws Exception {
        UUID schoolUuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        List<MinecraftProfile> profiles = List.of(new MinecraftProfile(schoolUuid, "SchoolUser"));
        ConfirmationSession session = new ConfirmationSession("confirmation", new PendingLink("7K3FP92AJQ",
                new BedrockIdentity(UUID.randomUUID(), "1234", "OriginBE"), Instant.now(), Instant.now().plusSeconds(300)),
                profiles, Instant.now(), Instant.now().plusSeconds(300));

        assertEquals(schoolUuid, ProfileSelection.select(session, 0).uuid());
        assertThrows(IllegalArgumentException.class, () -> ProfileSelection.select(session, 1));
    }
}
