package com.origin173.schoolBedrockLink.floodgate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.link.PlayerLink;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.floodgate.util.LinkedPlayer;

/** The only class that talks to Floodgate. It never accesses Floodgate's database directly. */
public final class FloodgateService implements LinkOperations {

    private final JavaPlugin plugin;
    private final Logger logger;
    private volatile FloodgateApi api;
    private volatile PlayerLink playerLink;
    private volatile String failure = "not initialized";

    public FloodgateService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void initialize() {
        try {
            Plugin floodgatePlugin = Bukkit.getPluginManager().getPlugin("floodgate");
            if (floodgatePlugin == null || !floodgatePlugin.isEnabled()) {
                failure = "Floodgate plugin is not enabled";
                return;
            }

            FloodgateApi foundApi = FloodgateApi.getInstance();
            if (foundApi == null) {
                failure = "FloodgateApi.getInstance() returned null";
                return;
            }
            PlayerLink foundLink = foundApi.getPlayerLink();
            if (foundLink == null || !foundLink.isEnabled() || foundLink.getName() == null) {
                failure = "Floodgate local PlayerLink is not enabled or not installed";
                return;
            }

            api = foundApi;
            playerLink = foundLink;
            failure = "";
            logger.info("Floodgate API ready; PlayerLink implementation: " + foundLink.getName());
        } catch (Throwable throwable) {
            api = null;
            playerLink = null;
            failure = "Floodgate API initialization failed";
            logger.severe("Floodgate API is unavailable; Bedrock access will fail closed.");
        }
    }

    public boolean isApiAvailable() {
        return api != null;
    }

    public boolean isReady() {
        try {
            PlayerLink link = playerLink;
            return api != null && link != null && link.isEnabled() && link.getName() != null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    public String failure() {
        return failure;
    }

    public String playerLinkName() {
        try {
            PlayerLink link = playerLink;
            return link == null ? "unavailable" : String.valueOf(link.getName());
        } catch (Throwable throwable) {
            return "unavailable";
        }
    }

    /**
     * Probe an event UUID without using isLinked(). A Java UUID is returned as JAVA;
     * an API failure is kept distinct so the listener can preserve Java compatibility.
     */
    public FloodgateProbe probe(UUID eventUuid) {
        FloodgateApi currentApi = api;
        if (currentApi == null) {
            return new FloodgateProbe(FloodgateProbe.Kind.API_UNAVAILABLE, null);
        }

        try {
            if (!currentApi.isFloodgatePlayer(eventUuid)) {
                return new FloodgateProbe(FloodgateProbe.Kind.JAVA, null);
            }
            return identityProbe(currentApi, eventUuid);
        } catch (Throwable throwable) {
            // A Floodgate UUID is still a reliable indication of a Bedrock path if
            // the online-player lookup itself failed. Never kick a Java player here.
            try {
                if (currentApi.isFloodgateId(eventUuid)) {
                    return new FloodgateProbe(FloodgateProbe.Kind.BEDROCK_IDENTITY_UNAVAILABLE, null);
                }
                return new FloodgateProbe(FloodgateProbe.Kind.JAVA, null);
            } catch (Throwable ignored) {
                return new FloodgateProbe(FloodgateProbe.Kind.API_UNAVAILABLE, null);
            }
        }
    }

    private FloodgateProbe identityProbe(FloodgateApi currentApi, UUID eventUuid) {
        FloodgatePlayer player = currentApi.getPlayer(eventUuid);
        if (player == null) {
            return new FloodgateProbe(FloodgateProbe.Kind.BEDROCK_IDENTITY_UNAVAILABLE, null);
        }
        BedrockIdentity identity = bedrockIdentity(player);
        if (identity == null) {
            return new FloodgateProbe(FloodgateProbe.Kind.BEDROCK_IDENTITY_UNAVAILABLE, null);
        }
        return new FloodgateProbe(FloodgateProbe.Kind.BEDROCK, identity);
    }

    /**
     * Builds the stable identity from Floodgate's own view of the player.
     *
     * <p>The Approved Registry and the Floodgate PlayerLink are both keyed by the Bedrock
     * UUID. As soon as a local link exists, the join event no longer carries that UUID: it
     * carries {@code getCorrectUniqueId()}, which is the linked Java UUID. Deriving the
     * identity from the event UUID therefore makes an already linked player look unapproved
     * forever, so the Bedrock UUID is always read from {@code getJavaUniqueId()}.
     *
     * @return the identity, or null when Floodgate exposes no usable identity (fail closed)
     */
    static BedrockIdentity bedrockIdentity(FloodgatePlayer player) {
        try {
            UUID bedrockUuid = player.getJavaUniqueId();
            String xuid = player.getXuid();
            String gamertag = player.getUsername();
            if (bedrockUuid == null || xuid == null || xuid.isBlank()
                    || gamertag == null || gamertag.isBlank()) {
                return null;
            }
            return new BedrockIdentity(bedrockUuid, xuid, gamertag);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public MappingLookup getMapping(UUID bedrockUuid, Duration timeout) {
        PlayerLink link = playerLink;
        if (!isReady()) {
            return MappingLookup.unavailable();
        }
        try {
            CompletableFuture<LinkedPlayer> future = link.getLinkedPlayer(bedrockUuid);
            LinkedPlayer linked = await(future, timeout);
            if (linked == null) {
                return MappingLookup.available(null);
            }
            UUID javaUuid = linked.getJavaUniqueId();
            if (javaUuid == null) {
                return MappingLookup.unavailable();
            }
            return MappingLookup.available(new FloodgateMapping(javaUuid, linked.getJavaUsername()));
        } catch (Throwable throwable) {
            return MappingLookup.unavailable();
        }
    }

    public boolean linkPlayer(UUID bedrockUuid, UUID javaUuid, String javaUsername, Duration timeout) {
        PlayerLink link = playerLink;
        if (!isReady()) {
            return false;
        }
        try {
            CompletableFuture<Void> future = link.linkPlayer(bedrockUuid, javaUuid, javaUsername);
            await(future, timeout);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    public boolean unlinkPlayer(UUID javaUuid, Duration timeout) {
        PlayerLink link = playerLink;
        if (!isReady()) {
            return false;
        }
        try {
            CompletableFuture<Void> future = link.unlinkPlayer(javaUuid);
            await(future, timeout);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static <T> T await(CompletableFuture<T> future, Duration timeout)
            throws Exception {
        if (future == null) {
            throw new IllegalStateException("Floodgate returned a null future");
        }
        long timeoutMillis = Math.max(1L, timeout.toMillis());
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }
}
