package com.origin173.schoolBedrockLink.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.origin173.schoolBedrockLink.floodgate.BedrockIdentity;
import com.origin173.schoolBedrockLink.util.UuidUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

/** Append-only audit trail containing no OAuth credentials or browser tokens. */
public final class AuditLogger {

    private final Path file;
    private final Logger logger;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuditLogger(Path dataFolder, Logger logger) {
        this.file = dataFolder.resolve("link-audit.jsonl");
        this.logger = logger;
    }

    public synchronized void record(String action, String result, BedrockIdentity identity,
                                    UUID javaUuid, String javaUsername) {
        ObjectNode event = mapper.createObjectNode();
        event.put("timestamp", Instant.now().toString());
        event.put("action", action);
        event.put("result", result);
        event.put("gamertag", identity.gamertag());
        event.put("bedrockUuid", UuidUtil.canonical(identity.bedrockUuid()));
        event.put("xuidMasked", maskXuid(identity.xuid()));
        if (javaUsername != null && !javaUsername.isBlank()) {
            event.put("javaUsername", javaUsername);
        }
        if (javaUuid != null) {
            event.put("javaUuid", UuidUtil.canonical(javaUuid));
        }

        try {
            Files.createDirectories(file.getParent());
            String line = mapper.writeValueAsString(event) + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            // The link operation itself remains authoritative; an audit failure is
            // visible to an administrator and never causes a token to be logged.
            logger.severe("Could not append SchoolBedrockLink audit log.");
        }
    }

    public Path file() {
        return file;
    }

    public static String maskXuid(String xuid) {
        if (xuid == null || xuid.isBlank()) {
            return "********";
        }
        String value = xuid.trim();
        if (value.length() <= 8) {
            return "********";
        }
        int prefixLength = Math.min(6, value.length() - 4);
        return value.substring(0, prefixLength) + "******" + value.substring(value.length() - 4);
    }
}
