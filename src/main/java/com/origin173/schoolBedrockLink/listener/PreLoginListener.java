package com.origin173.schoolBedrockLink.listener;

import com.origin173.schoolBedrockLink.SchoolBedrockLink;
import com.origin173.schoolBedrockLink.config.PluginConfig;
import com.origin173.schoolBedrockLink.floodgate.BedrockIdentity;
import com.origin173.schoolBedrockLink.floodgate.FloodgateProbe;
import com.origin173.schoolBedrockLink.floodgate.FloodgateService;
import com.origin173.schoolBedrockLink.link.LinkService;
import com.origin173.schoolBedrockLink.link.PendingLink;
import com.origin173.schoolBedrockLink.link.PendingLinkService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class PreLoginListener implements Listener {

    private final SchoolBedrockLink plugin;
    private final FloodgateService floodgate;
    private final LinkService links;
    private final PendingLinkService pending;
    private final AtomicLong lastUnavailableLog = new AtomicLong();

    public PreLoginListener(SchoolBedrockLink plugin, FloodgateService floodgate,
                            LinkService links, PendingLinkService pending) {
        this.plugin = plugin;
        this.floodgate = floodgate;
        this.links = links;
        this.pending = pending;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID eventUuid = event.getUniqueId();
        FloodgateProbe probe = floodgate.probe(eventUuid);
        if (probe.kind() == FloodgateProbe.Kind.JAVA) {
            // Hard requirement: Java players are outside this plugin's gate.
            return;
        }
        if (probe.kind() == FloodgateProbe.Kind.API_UNAVAILABLE) {
            // There is no safe way to distinguish a Java UUID after an API outage.
            // Preserve the existing Java login path; Floodgate startup failure is
            // already reported and all identifiable Bedrock paths fail closed.
            logUnavailableOnce();
            return;
        }

        PluginConfig config = plugin.configuration();
        if (probe.kind() != FloodgateProbe.Kind.BEDROCK || !floodgate.isReady()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, config.messages().notReady());
            return;
        }
        BedrockIdentity identity = probe.identity();
        if (identity == null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, config.messages().notReady());
            return;
        }

        LinkService.AccessOutcome outcome = links.checkAccess(identity,
                config.binding().autoRepairMissingFloodgateLink());
        switch (outcome.status()) {
            case ALLOW -> {
                return;
            }
            case REPAIRED -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    config.messages().repaired());
            case CONFLICT -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    config.messages().conflict());
            case NOT_READY -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    config.messages().notReady());
            case UNAPPROVED -> {
                if (!config.oauthConfigured() || !plugin.httpServer().isRunning()) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, config.messages().notReady());
                    return;
                }
                PendingLink request = pending.getOrCreate(identity, config.binding().codeLength(),
                        config.binding().codeExpireSeconds(), config.binding().reuseExistingCode(), Instant.now());
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        unlinkedMessage(config, request));
            }
        }
    }

    private String unlinkedMessage(PluginConfig config, PendingLink request) {
        long seconds = Math.max(1L, Duration.between(Instant.now(), request.expiresAt()).toSeconds());
        long minutes = Math.max(1L, (seconds + 59L) / 60L);
        return config.messages().unlinked()
                .replace("{code}", request.bindingCode())
                .replace("{minutes}", Long.toString(minutes))
                .replace("{url}", config.http().publicBaseUrl());
    }

    private void logUnavailableOnce() {
        long now = System.currentTimeMillis();
        long previous = lastUnavailableLog.get();
        if (now - previous > 60_000L && lastUnavailableLog.compareAndSet(previous, now)) {
            Logger logger = plugin.getLogger();
            logger.severe("Floodgate API could not classify a pre-login UUID; no Java player was kicked.");
        }
    }
}
