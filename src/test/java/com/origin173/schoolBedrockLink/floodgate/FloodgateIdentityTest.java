package com.origin173.schoolBedrockLink.floodgate;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FloodgateIdentityTest {

    private static final UUID BEDROCK_UUID = UUID.fromString("00000000-0000-0000-0009-01f2a5eb7347");
    private static final UUID JAVA_UUID = UUID.fromString("853b9ef7-d58c-329f-9a53-c4e509a4f309");

    @Test
    void linkedPlayerIdentityStillUsesTheBedrockUuid() {
        // Once Local Linking exists the join event carries the Java UUID as
        // getCorrectUniqueId(); the registry key must stay the Bedrock UUID.
        FloodgatePlayer player = player(BEDROCK_UUID, JAVA_UUID);

        BedrockIdentity identity = FloodgateService.bedrockIdentity(player);

        assertEquals(BEDROCK_UUID, identity.bedrockUuid());
        assertEquals("2535412345678901", identity.xuid());
        assertEquals("SunnyFy7896", identity.gamertag());
    }

    @Test
    void unlinkedPlayerIdentityUsesTheBedrockUuid() {
        FloodgatePlayer player = player(BEDROCK_UUID, BEDROCK_UUID);

        BedrockIdentity identity = FloodgateService.bedrockIdentity(player);

        assertEquals(BEDROCK_UUID, identity.bedrockUuid());
    }

    @Test
    void incompleteFloodgateIdentityFailsClosed() {
        assertNull(FloodgateService.bedrockIdentity(player(null, JAVA_UUID)));
        assertNull(FloodgateService.bedrockIdentity(player(BEDROCK_UUID, JAVA_UUID, " ", "SunnyFy7896")));
        assertNull(FloodgateService.bedrockIdentity(player(BEDROCK_UUID, JAVA_UUID, "123", "")));
    }

    @Test
    void identityRejectsValuesFloodgateShouldNeverReturn() {
        assertThrows(IllegalArgumentException.class,
                () -> new BedrockIdentity(BEDROCK_UUID, "x".repeat(129), "SunnyFy7896"));
    }

    private static FloodgatePlayer player(UUID bedrockUuid, UUID correctUniqueId) {
        return player(bedrockUuid, correctUniqueId, "2535412345678901", "SunnyFy7896");
    }

    /**
     * FloodgatePlayer is a wide interface; a proxy keeps this stub limited to the
     * methods the identity resolution is allowed to use.
     */
    private static FloodgatePlayer player(UUID bedrockUuid, UUID correctUniqueId,
                                          String xuid, String username) {
        return (FloodgatePlayer) Proxy.newProxyInstance(
                FloodgatePlayer.class.getClassLoader(),
                new Class<?>[]{FloodgatePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getJavaUniqueId" -> bedrockUuid;
                    case "getCorrectUniqueId" -> correctUniqueId;
                    case "getXuid" -> xuid;
                    case "getUsername" -> username;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
