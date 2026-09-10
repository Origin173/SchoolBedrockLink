package com.origin173.schoolBedrockLink.http;

import com.origin173.schoolBedrockLink.config.PluginConfig;
import com.origin173.schoolBedrockLink.floodgate.FloodgateService;
import com.origin173.schoolBedrockLink.link.ApprovedLinkRegistry;
import com.origin173.schoolBedrockLink.link.LinkService;
import com.origin173.schoolBedrockLink.link.PendingLinkService;
import com.origin173.schoolBedrockLink.oauth.BlessingSkinProfileService;
import com.origin173.schoolBedrockLink.oauth.OAuthService;
import com.origin173.schoolBedrockLink.oauth.OAuthSessionService;
import com.origin173.schoolBedrockLink.security.RateLimiter;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.logging.Logger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class WebContext {

    private final Supplier<PluginConfig> config;
    private final PendingLinkService pending;
    private final OAuthSessionService sessions;
    private final OAuthService oauth;
    private final BlessingSkinProfileService blessingSkinProfiles;
    private final LinkService links;
    private final ApprovedLinkRegistry registry;
    private final FloodgateService floodgate;
    private final RateLimiter ipLimiter;
    private final RateLimiter oauthStartLimiter;
    private final BooleanSupplier httpRunning;
    private final Logger logger;

    public WebContext(Supplier<PluginConfig> config, PendingLinkService pending,
                      OAuthSessionService sessions, OAuthService oauth,
                      BlessingSkinProfileService blessingSkinProfiles, LinkService links,
                      ApprovedLinkRegistry registry, FloodgateService floodgate,
                      RateLimiter ipLimiter, RateLimiter oauthStartLimiter,
                      BooleanSupplier httpRunning, Logger logger) {
        this.config = config;
        this.pending = pending;
        this.sessions = sessions;
        this.oauth = oauth;
        this.blessingSkinProfiles = blessingSkinProfiles;
        this.links = links;
        this.registry = registry;
        this.floodgate = floodgate;
        this.ipLimiter = ipLimiter;
        this.oauthStartLimiter = oauthStartLimiter;
        this.httpRunning = httpRunning;
        this.logger = logger;
    }

    public PluginConfig config() {
        return config.get();
    }

    public PendingLinkService pending() {
        return pending;
    }

    public OAuthSessionService sessions() {
        return sessions;
    }

    public OAuthService oauth() {
        return oauth;
    }

    public BlessingSkinProfileService blessingSkinProfiles() {
        return blessingSkinProfiles;
    }

    public LinkService links() {
        return links;
    }

    public ApprovedLinkRegistry registry() {
        return registry;
    }

    public FloodgateService floodgate() {
        return floodgate;
    }

    public boolean allowRequest(HttpExchange exchange) {
        PluginConfig current = config();
        return !current.rateLimit().enabled() || ipLimiter.tryAcquire(clientKey(exchange));
    }

    public boolean allowOAuthStart(String code) {
        PluginConfig current = config();
        return !current.rateLimit().enabled()
                || (code != null && oauthStartLimiter.tryAcquire(code));
    }

    public void updateLimiters() {
        PluginConfig current = config();
        ipLimiter.updateMaxRequests(current.rateLimit().perIpPerMinute());
        oauthStartLimiter.updateMaxRequests(current.rateLimit().oauthStartPerCode());
    }

    public boolean httpRunning() {
        return httpRunning.getAsBoolean();
    }

    public Logger logger() {
        return logger;
    }

    private static String clientKey(HttpExchange exchange) {
        InetSocketAddress remoteAddress = exchange == null ? null : exchange.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null
                && remoteAddress.getAddress().isLoopbackAddress()) {
            List<String> forwardedValues = exchange.getRequestHeaders().get("X-Forwarded-For");
            if (forwardedValues != null && forwardedValues.size() == 1) {
                String forwarded = forwardedValues.get(0);
                String client = trustedForwardedClient(forwarded);
                if (client != null) {
                    return "forwarded:" + client;
                }
            }
        }
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    static String trustedForwardedClient(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.isBlank() || candidate.length() > 128 || candidate.indexOf(',') >= 0
                || !isSafeForwardedAddress(candidate)) {
            return null;
        }
        try {
            return java.net.InetAddress.ofLiteral(candidate).getHostAddress();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean isSafeForwardedAddress(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(Character.isLetterOrDigit(character) || character == '.' || character == ':'
                    || character == '%')) {
                return false;
            }
        }
        return true;
    }
}
