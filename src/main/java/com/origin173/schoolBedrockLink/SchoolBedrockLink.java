package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.audit.AuditLogger;
import com.origin173.schoolBedrockLink.command.SchoolLinkCommand;
import com.origin173.schoolBedrockLink.config.ConfigLoader;
import com.origin173.schoolBedrockLink.config.PluginConfig;
import com.origin173.schoolBedrockLink.floodgate.FloodgateService;
import com.origin173.schoolBedrockLink.http.EmbeddedHttpServer;
import com.origin173.schoolBedrockLink.http.WebContext;
import com.origin173.schoolBedrockLink.link.ApprovedLinkRegistry;
import com.origin173.schoolBedrockLink.link.LinkService;
import com.origin173.schoolBedrockLink.link.PendingLinkService;
import com.origin173.schoolBedrockLink.listener.PreLoginListener;
import com.origin173.schoolBedrockLink.oauth.BlessingSkinProfileService;
import com.origin173.schoolBedrockLink.oauth.OAuthService;
import com.origin173.schoolBedrockLink.oauth.OAuthSessionService;
import com.origin173.schoolBedrockLink.security.RateLimiter;
import com.origin173.schoolBedrockLink.security.SecureTokenGenerator;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;

public final class SchoolBedrockLink extends JavaPlugin {

    private volatile PluginConfig configuration;
    private com.origin173.schoolBedrockLink.config.ConfigurationStore configurationStore;
    private ApprovedLinkRegistry registry;
    private PendingLinkService pending;
    private OAuthSessionService sessions;
    private FloodgateService floodgate;
    private OAuthService oauth;
    private BlessingSkinProfileService blessingSkinProfiles;
    private LinkService links;
    private EmbeddedHttpServer httpServer;
    private WebContext webContext;
    private RateLimiter ipLimiter;
    private RateLimiter oauthStartLimiter;
    private int cleanupTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            configuration = ConfigLoader.load(this);
            configurationStore = new com.origin173.schoolBedrockLink.config.ConfigurationStore(configuration);
        } catch (RuntimeException exception) {
            getLogger().severe("Could not load config.yml; plugin will be disabled and Java login remains untouched.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SecureTokenGenerator tokens = new SecureTokenGenerator();
        registry = new ApprovedLinkRegistry(getDataFolder().toPath(), getLogger());
        pending = new PendingLinkService(tokens);
        sessions = new OAuthSessionService(tokens);
        floodgate = new FloodgateService(this);
        floodgate.initialize();
        AuditLogger audit = new AuditLogger(getDataFolder().toPath(), getLogger());
        oauth = new OAuthService(configuration.oauth(),
                Duration.ofSeconds(configuration.http().requestTimeoutSeconds()));
        blessingSkinProfiles = new BlessingSkinProfileService(
                Duration.ofSeconds(configuration.http().requestTimeoutSeconds()), getLogger());
        links = new LinkService(registry, floodgate, audit, getLogger(),
                Duration.ofSeconds(configuration.http().requestTimeoutSeconds()));
        ipLimiter = new RateLimiter(configuration.rateLimit().perIpPerMinute(), Duration.ofMinutes(1));
        oauthStartLimiter = new RateLimiter(configuration.rateLimit().oauthStartPerCode(), Duration.ofMinutes(5));
        webContext = new WebContext(this::configuration, pending, sessions, oauth, blessingSkinProfiles, links, registry,
                floodgate, ipLimiter, oauthStartLimiter,
                () -> httpServer != null && httpServer.isRunning(), getLogger());
        httpServer = new EmbeddedHttpServer(webContext);

        getServer().getPluginManager().registerEvents(new PreLoginListener(this, floodgate, links, pending), this);
        if (getCommand("schoollink") != null) {
            SchoolLinkCommand command = new SchoolLinkCommand(this);
            getCommand("schoollink").setExecutor(command);
            getCommand("schoollink").setTabCompleter(command);
        }

        try {
            httpServer.start(configuration.http());
            getLogger().info("HTTP server listening on " + configuration.http().bindAddress() + ":"
                    + configuration.http().port());
        } catch (IOException | RuntimeException exception) {
            getLogger().severe("Could not start the embedded HTTP server; Bedrock access will fail closed.");
        }

        cleanupTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            Instant now = Instant.now();
            pending.cleanup(now);
            sessions.cleanup(now);
            ipLimiter.cleanup();
            oauthStartLimiter.cleanup();
        }, 20L * 60L, 20L * 60L).getTaskId();

        getLogger().info("SchoolBedrockLink " + (isReady() ? "READY" : "NOT READY"));
        if (!registry.isHealthy()) {
            getLogger().severe("Approved Registry is unhealthy; do not delete approved-links.json or approved-links.json.bak.");
        }
    }

    @Override
    public void onDisable() {
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
            cleanupTaskId = -1;
        }
        if (httpServer != null) {
            httpServer.stop();
        }
    }

    public synchronized boolean reloadPluginConfig() {
        try {
            Path file = getDataFolder().toPath().resolve("config.yml");
            PluginConfig loaded = configurationStore.reload(file, getLogger());
            if (!configuration.http().bindAddress().equals(loaded.http().bindAddress())
                    || configuration.http().port() != loaded.http().port()
                    || !configuration.http().publicBaseUrl().equals(loaded.http().publicBaseUrl())
                    || configuration.http().requestTimeoutSeconds() != loaded.http().requestTimeoutSeconds()) {
                getLogger().warning("HTTP bind-address, port, public-base-url, or request-timeout changes require a server restart; keeping the current listener settings.");
            }
            boolean pendingCodeSettingsChanged = configuration.binding().codeLength()
                    != loaded.binding().codeLength()
                    || configuration.binding().codeExpireSeconds() != loaded.binding().codeExpireSeconds()
                    || configuration.binding().reuseExistingCode() != loaded.binding().reuseExistingCode();
            configuration = configuration.withReloadableFrom(loaded);
            if (pendingCodeSettingsChanged) {
                pending.clear();
                getLogger().info("Binding settings changed; existing authentication codes were invalidated.");
            }
            if (pendingCodeSettingsChanged) {
                sessions.clear();
                getLogger().info("In-flight OAuth sessions were invalidated after binding settings changed.");
            }
            oauth.updateConfig(configuration.oauth());
            webContext.updateLimiters();
            return true;
        } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
            getLogger().severe("Could not reload config.yml; the previous configuration is still active."
                    + " reason=" + exception.getClass().getSimpleName());
            return false;
        }
    }

    public PluginConfig configuration() {
        return configuration;
    }

    public ApprovedLinkRegistry registry() {
        return registry;
    }

    public PendingLinkService pending() {
        return pending;
    }

    public FloodgateService floodgate() {
        return floodgate;
    }

    public LinkService links() {
        return links;
    }

    public EmbeddedHttpServer httpServer() {
        return httpServer;
    }

    public boolean isReady() {
        return configuration != null && configuration.oauthConfigured()
                && registry != null && registry.isHealthy()
                && floodgate != null && floodgate.isReady()
                && httpServer != null && httpServer.isRunning();
    }
}
