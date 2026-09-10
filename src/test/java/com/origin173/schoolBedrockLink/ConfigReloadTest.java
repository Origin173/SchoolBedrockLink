package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.config.BindingConfig;
import com.origin173.schoolBedrockLink.config.HttpConfig;
import com.origin173.schoolBedrockLink.config.MessageConfig;
import com.origin173.schoolBedrockLink.config.OAuthConfig;
import com.origin173.schoolBedrockLink.config.PluginConfig;
import com.origin173.schoolBedrockLink.config.RateLimitConfig;
import java.time.Duration;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigReloadTest {

    @Test
    void reloadAppliesNewBindingLifetime() {
        BindingConfig currentBinding = new BindingConfig(10, 300, true, true);
        BindingConfig loadedBinding = new BindingConfig(12, 900, false, false);

        PluginConfig reloaded = config(currentBinding).withReloadableFrom(config(loadedBinding));

        assertEquals(loadedBinding, reloaded.binding());
        assertEquals(Duration.ofSeconds(900), reloaded.bindingLifetime());
    }

    private static PluginConfig config(BindingConfig binding) {
        OAuthConfig oauth = new OAuthConfig("blessing-skin", URI.create("https://example.test/authorize"),
                URI.create("https://example.test/token"), URI.create("https://example.test/user"),
                URI.create("https://example.test/players"), URI.create("https://example.test/profiles"),
                "client", "secret", List.of(), true);
        return new PluginConfig(new HttpConfig("127.0.0.1", 8787, "https://example.test", 10), oauth,
                binding, new RateLimitConfig(true, 30, 5),
                new MessageConfig("unlinked", "repaired", "conflict", "not-ready"), false);
    }
}
