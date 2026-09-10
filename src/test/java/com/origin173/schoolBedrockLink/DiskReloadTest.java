package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.config.*;
import java.nio.file.*;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DiskReloadTest {
    @TempDir Path directory;

    private String yaml(int lifetime, String base) {
        return """
                http:
                  bind-address: 127.0.0.1
                  port: 8787
                  public-base-url: %s
                  request-timeout-seconds: 2
                oauth:
                  mode: blessing-skin
                  authorization-url: https://skin.example.test/authorize
                  token-url: https://skin.example.test/token
                  user-endpoint: https://skin.example.test/user
                  players-endpoint: https://skin.example.test/players
                  yggdrasil-profiles-endpoint: https://skin.example.test/profiles
                  client-id: test-client
                  client-secret: test-secret
                  pkce: true
                binding:
                  code-expire-seconds: %d
                """.formatted(base, lifetime);
    }

    @Test
    void reloadReadsEditedFileAndRetainsLastValidSnapshotOnFailure() throws Exception {
        Path file = directory.resolve("config.yml");
        Logger logger = Logger.getAnonymousLogger();
        Files.writeString(file, yaml(300, "https://auth.example.test"));
        var store = new ConfigurationStore(ConfigLoader.loadFromFile(file, logger, false));
        Files.writeString(file, yaml(900, "https://new.example.test"));
        store.reload(file, logger);
        assertEquals(900, store.current().binding().codeExpireSeconds());
        assertEquals("https://auth.example.test/oauth/callback", store.current().http().callbackUrl());
        var valid = store.current();
        Files.writeString(file, yaml(20, "https://auth.example.test"));
        assertThrows(IllegalArgumentException.class, () -> store.reload(file, logger));
        assertSame(valid, store.current());
        Files.writeString(file, "oauth: [broken");
        assertThrows(Exception.class, () -> store.reload(file, logger));
        assertSame(valid, store.current());
        Files.writeString(file, yaml(300, "https://127.0.0.1"));
        assertThrows(IllegalArgumentException.class, () -> store.reload(file, logger));
        assertSame(valid, store.current());
    }
}
