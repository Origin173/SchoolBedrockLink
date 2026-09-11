package com.origin173.schoolBedrockLink.config;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigLoader {

    private static final String DEFAULT_UNLINKED = "§e校园 Minecraft 身份认证\n\n"
            + "§f你的 Xbox 账号还没有绑定学校 Minecraft 账号。\n\n"
            + "§f请打开：\n§b{url}\n\n"
            + "§f认证码：\n§a{code}\n\n"
            + "§7验证码 {minutes} 分钟内有效。\n§7认证完成以后重新进入服务器。";
    private static final String DEFAULT_REPAIRED = "§a账号绑定已经自动修复。\n§f请重新连接服务器。";
    private static final String DEFAULT_CONFLICT = "§c账号绑定发生冲突。\n§f请联系服务器管理员。";
    private static final String DEFAULT_NOT_READY = "§c校园身份认证服务暂时不可用。\n"
            + "§f请稍后重试；如果持续出现，请联系服务器管理员。";

    private ConfigLoader() {
    }

    public static PluginConfig load(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return load(plugin.getConfig(), plugin.getLogger(), null, false);
    }

    /** Read the on-disk file without mutating the plugin's live configuration. */
    public static PluginConfig loadFromFile(Path file, Logger logger, boolean developmentMode)
            throws IOException, InvalidConfigurationException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(logger, "logger");
        if (!Files.isRegularFile(file)) {
            throw new IOException("config.yml does not exist");
        }
        YamlConfiguration source = new YamlConfiguration();
        source.load(file.toFile());
        validateReloadValues(source);
        return load(source, logger, developmentMode, true);
    }

    private static void validateReloadValues(FileConfiguration source) {
        if (source.contains("http.bind-address") && (!source.isString("http.bind-address")
                || source.getString("http.bind-address", "").isBlank())) {
            throw new IllegalArgumentException("Invalid field: http.bind-address");
        }
        if (source.contains("oauth.scopes")) {
            var scopes = source.getList("oauth.scopes");
            if (scopes == null || scopes.stream().anyMatch(value -> !(value instanceof String scope)
                    || scope.isBlank() || scope.length() > 128)) {
                throw new IllegalArgumentException("Invalid field: oauth.scopes");
            }
        }
        String[] keys = {"http.port", "http.request-timeout-seconds", "binding.code-length",
                "binding.code-expire-seconds", "rate-limit.per-ip-per-minute", "rate-limit.oauth-start-per-code"};
        int[] minimum = {1, 1, 8, 60, 1, 1};
        int[] maximum = {65535, 60, 32, 3600, 10000, 1000};
        for (int i = 0; i < keys.length; i++) {
            if (source.contains(keys[i]) && (!source.isInt(keys[i])
                    || source.getInt(keys[i]) < minimum[i] || source.getInt(keys[i]) > maximum[i])) {
                throw new IllegalArgumentException("Invalid field: " + keys[i]);
            }
        }
        for (String key : List.of("development-mode", "oauth.pkce", "binding.reuse-existing-code",
                "binding.auto-repair-missing-floodgate-link", "rate-limit.enabled")) {
            if (source.contains(key) && !source.isBoolean(key)) {
                throw new IllegalArgumentException("Invalid field: " + key);
            }
        }
    }

    private static PluginConfig load(FileConfiguration source, Logger logger,
                                     Boolean developmentModeOverride, boolean strict) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(logger, "logger");

        boolean developmentMode = developmentModeOverride == null
                ? source.getBoolean("development-mode", false) : developmentModeOverride;

        String bindAddress = string(source, "http.bind-address", "127.0.0.1");
        if (bindAddress.isBlank()) {
            logger.warning("http.bind-address is blank; using the secure localhost default 127.0.0.1.");
            bindAddress = "127.0.0.1";
        }
        int port = boundedInt(source, "http.port", 8787, 1, 65535, logger);
        String publicBaseUrl = normalizeBaseUrl(string(source, "http.public-base-url", ""), developmentMode, logger);
        int timeoutSeconds = boundedInt(source, "http.request-timeout-seconds", 10, 1, 60, logger);
        HttpConfig http = new HttpConfig(bindAddress, port, publicBaseUrl, timeoutSeconds);

        String mode = string(source, "oauth.mode", "blessing-skin").toLowerCase(Locale.ROOT);
        URI authorizationUrl = uri(source.getString("oauth.authorization-url"), "oauth.authorization-url",
                developmentMode, logger);
        URI tokenUrl = uri(source.getString("oauth.token-url"), "oauth.token-url", developmentMode, logger);
        URI userEndpoint = uri(source.getString("oauth.user-endpoint"), "oauth.user-endpoint",
                developmentMode, logger);
        URI playersEndpoint = uri(source.getString("oauth.players-endpoint"), "oauth.players-endpoint",
                developmentMode, logger);
        URI yggdrasilProfilesEndpoint = uri(source.getString("oauth.yggdrasil-profiles-endpoint"),
                "oauth.yggdrasil-profiles-endpoint", developmentMode, logger);
        String clientId = string(source, "oauth.client-id", "");
        String clientSecret = resolveSecret(string(source, "oauth.client-secret", ""), logger);
        List<String> scopes = new ArrayList<>();
        for (String scope : source.getStringList("oauth.scopes")) {
            if (scope != null && !scope.isBlank() && scope.length() <= 128) {
                String normalizedScope = scope.trim();
                if ("offline_access".equalsIgnoreCase(normalizedScope)) {
                    logger.warning("oauth.scopes contains offline_access; it is ignored because refresh tokens are not used.");
                    continue;
                }
                scopes.add(normalizedScope);
            }
        }
        boolean pkce = source.getBoolean("oauth.pkce", true);
        OAuthConfig oauth = new OAuthConfig(mode, authorizationUrl, tokenUrl, userEndpoint, playersEndpoint,
                yggdrasilProfilesEndpoint, clientId, clientSecret, scopes, pkce);

        int codeLength = boundedInt(source, "binding.code-length", 10, 8, 32, logger);
        int codeExpireSeconds = boundedInt(source, "binding.code-expire-seconds", 300, 60, 3600, logger);
        BindingConfig binding = new BindingConfig(codeLength, codeExpireSeconds,
                source.getBoolean("binding.reuse-existing-code", true),
                source.getBoolean("binding.auto-repair-missing-floodgate-link", true));

        boolean rateLimitEnabled = source.getBoolean("rate-limit.enabled", true);
        int perIp = boundedInt(source, "rate-limit.per-ip-per-minute", 30, 1, 10000, logger);
        int perCode = boundedInt(source, "rate-limit.oauth-start-per-code", 5, 1, 1000, logger);
        RateLimitConfig rateLimit = new RateLimitConfig(rateLimitEnabled, perIp, perCode);

        MessageConfig messages = new MessageConfig(
                string(source, "messages.unlinked", DEFAULT_UNLINKED),
                string(source, "messages.repaired", DEFAULT_REPAIRED),
                string(source, "messages.conflict", DEFAULT_CONFLICT),
                string(source, "messages.not-ready", DEFAULT_NOT_READY));

        if (!pkce) {
            logger.severe("oauth.pkce must remain true; OAuth is NOT READY until PKCE S256 is enabled.");
        }
        if (!publicBaseUrl.isBlank() && !publicBaseUrl.startsWith("https://")) {
            logger.warning("http.public-base-url is not HTTPS; use HTTPS in production and an HTTPS OAuth redirect URI.");
        }
        if (clientSecret.isBlank()) {
            logger.warning("OAuth is NOT READY: client secret environment variable is missing.");
        }

        if (strict) {
            if (publicBaseUrl.isBlank() || !oauth.configured(http.callbackUrl())) {
                throw new IllegalArgumentException("configuration is incomplete; OAuth is not ready");
            }
            if (!pkce) {
                throw new IllegalArgumentException("oauth.pkce must be true");
            }
        }

        return new PluginConfig(http, oauth, binding, rateLimit, messages, developmentMode);
    }

    private static String string(FileConfiguration source, String path, String fallback) {
        String value = source.getString(path);
        return value == null ? fallback : value.trim();
    }

    private static int boundedInt(FileConfiguration source, String path, int fallback,
                                  int minimum, int maximum, Logger logger) {
        int value = source.getInt(path, fallback);
        if (value < minimum || value > maximum) {
            logger.warning(path + " is outside the supported range; using " + fallback + ".");
            return fallback;
        }
        return value;
    }

    private static URI uri(String raw, String path, boolean developmentMode, Logger logger) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(raw.trim());
            if (!isAllowedHttpUrl(uri, developmentMode) || uri.getFragment() != null) {
                throw new IllegalArgumentException("not an HTTP(S) URL");
            }
            return uri;
        } catch (RuntimeException exception) {
            logger.warning(path + " is invalid; OAuth is NOT READY.");
            return null;
        }
    }

    private static String normalizeBaseUrl(String raw, boolean developmentMode, Logger logger) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(raw.trim());
            if (!isAllowedHttpUrl(uri, developmentMode) || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !uri.getRawPath().matches("(?:/[A-Za-z0-9_-]+)*/?")
                    || (!developmentMode && isLoopbackHost(uri.getHost()))) {
                throw new IllegalArgumentException("invalid public base URL");
            }
            return raw.trim().replaceAll("/+$", "");
        } catch (RuntimeException exception) {
            logger.warning("http.public-base-url is invalid; OAuth is NOT READY.");
            return "";
        }
    }

    private static boolean isAllowedHttpUrl(URI uri, boolean developmentMode) {
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null) {
            return false;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return true;
        }
        return "http".equalsIgnoreCase(uri.getScheme())
                && developmentMode && isLoopbackHost(uri.getHost());
    }

    private static boolean isLoopbackHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return "localhost".equals(normalized) || "::1".equals(normalized)
                || "127.0.0.1".equals(normalized) || normalized.startsWith("127.");
    }

    private static String resolveSecret(String configured, Logger logger) {
        if (configured == null || configured.isBlank()) {
            return "";
        }
        String value = configured.trim();
        if (value.startsWith("${ENV:") && value.endsWith("}")) {
            String environmentName = value.substring("${ENV:".length(), value.length() - 1);
            if (!environmentName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                logger.warning("oauth.client-secret contains an invalid environment variable reference.");
                return "";
            }
            String secret = System.getenv(environmentName);
            return secret == null ? "" : secret;
        }
        // Literal secrets are accepted for local development, but are never logged.
        logger.warning("oauth.client-secret is configured as a literal; use ${ENV:...} outside local development.");
        return value;
    }
}
