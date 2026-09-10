package com.origin173.schoolBedrockLink.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;

/** Immutable configuration is published only after the entire disk candidate validates. */
public final class ConfigurationStore {
    private volatile PluginConfig current;
    public ConfigurationStore(PluginConfig initial) { current = initial; }
    public PluginConfig current() { return current; }
    public synchronized PluginConfig reload(Path file, Logger logger)
            throws IOException, InvalidConfigurationException {
        PluginConfig loaded = ConfigLoader.loadFromFile(file, logger, current.developmentMode());
        if (!current.http().equals(loaded.http())) {
            logger.warning("HTTP settings require restart; current listener, public URL and timeout retained.");
        }
        current = current.withReloadableFrom(loaded);
        return current;
    }
}
