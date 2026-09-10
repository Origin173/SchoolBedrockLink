package com.origin173.schoolBedrockLink.config;

import java.time.Duration;

public record PluginConfig(HttpConfig http, OAuthConfig oauth, BindingConfig binding,
                           RateLimitConfig rateLimit, MessageConfig messages,
                           boolean developmentMode) {

    public boolean oauthConfigured() {
        return oauth != null && oauth.configured(http.callbackUrl());
    }

    public Duration bindingLifetime() {
        return Duration.ofSeconds(binding.codeExpireSeconds());
    }

    /** Reload only settings that are safe to apply without restarting the listener. */
    public PluginConfig withReloadableFrom(PluginConfig loaded) {
        return new PluginConfig(http, loaded.oauth, loaded.binding, loaded.rateLimit, loaded.messages, developmentMode);
    }
}
