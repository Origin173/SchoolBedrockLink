package com.origin173.schoolBedrockLink.link;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.origin173.schoolBedrockLink.util.AtomicFileWriter;
import com.origin173.schoolBedrockLink.util.UuidUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/** Persistent proof that a Floodgate mapping was approved by school OAuth. */
public final class ApprovedLinkRegistry {

    private static final int SCHEMA_VERSION = 1;

    private final Path file;
    private final Logger logger;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<UUID, ApprovedLink> byBedrock = new HashMap<>();
    private final Map<UUID, ApprovedLink> byJava = new HashMap<>();
    private volatile boolean healthy;

    public ApprovedLinkRegistry(Path dataFolder, Logger logger) {
        this.file = dataFolder.resolve("approved-links.json");
        this.logger = logger;
        try {
            Files.createDirectories(dataFolder);
            if (Files.exists(file)) {
                loadExisting();
            } else {
                persist(List.of());
                healthy = true;
            }
        } catch (Exception exception) {
            healthy = false;
            logger.severe("approved-links.json is missing or cannot be loaded; Bedrock access will fail closed.");
        }
    }

    public synchronized boolean isHealthy() {
        return healthy;
    }

    public Path file() {
        return file;
    }

    public synchronized ApprovedLink findByBedrock(UUID bedrockUuid) {
        return byBedrock.get(bedrockUuid);
    }

    public synchronized ApprovedLink findByJava(UUID javaUuid) {
        return byJava.get(javaUuid);
    }

    public synchronized ApprovedLink findByXuid(String xuid) {
        if (xuid == null || xuid.isBlank()) {
            return null;
        }
        String normalized = xuid.trim();
        var matches = byBedrock.values().stream()
                .filter(link -> link.xuid().equals(normalized))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    public synchronized ApprovedLink resolveIdentifier(String identifier) {
        if (!healthy || identifier == null) return null;
        String value = identifier.trim();
        if (value.startsWith("xuid:")) return findByXuid(value.substring(5));
        try {
            if (value.startsWith("java:")) return findByJava(UUID.fromString(value.substring(5)));
            if (value.startsWith("bedrock:")) return findByBedrock(UUID.fromString(value.substring(8)));
            UUID uuid = UUID.fromString(value);
            var java = findByJava(uuid);
            var bedrock = findByBedrock(uuid);
            if (java != null && bedrock != null && !java.equals(bedrock)) return null;
            return java != null ? java : bedrock;
        } catch (IllegalArgumentException exception) { return findByXuid(value); }
    }

    public synchronized List<ApprovedLink> snapshot() {
        return byBedrock.values().stream()
                .sorted(Comparator.comparing(link -> link.bedrockUuid().toString()))
                .toList();
    }

    public synchronized boolean save(ApprovedLink link) {
        if (!healthy) {
            return false;
        }
        ApprovedLink existingBedrock = byBedrock.get(link.bedrockUuid());
        if (existingBedrock != null && !existingBedrock.javaUuid().equals(link.javaUuid())) {
            return false;
        }
        ApprovedLink existingJava = byJava.get(link.javaUuid());
        if (existingJava != null && !existingJava.bedrockUuid().equals(link.bedrockUuid())) {
            return false;
        }

        Map<UUID, ApprovedLink> next = new HashMap<>(byBedrock);
        next.put(link.bedrockUuid(), link);
        try {
            persist(next.values());
            byBedrock.clear();
            byJava.clear();
            for (ApprovedLink saved : next.values()) {
                byBedrock.put(saved.bedrockUuid(), saved);
                byJava.put(saved.javaUuid(), saved);
            }
            return true;
        } catch (IOException exception) {
            healthy = false;
            logger.severe("Could not safely write approved-links.json; Bedrock access is now fail closed.");
            return false;
        }
    }

    public synchronized boolean removeByBedrock(UUID bedrockUuid) {
        if (!healthy) {
            return false;
        }
        ApprovedLink removed = byBedrock.get(bedrockUuid);
        if (removed == null) {
            return false;
        }
        Map<UUID, ApprovedLink> next = new HashMap<>(byBedrock);
        next.remove(bedrockUuid);
        try {
            persist(next.values());
            byBedrock.clear();
            byJava.clear();
            for (ApprovedLink saved : next.values()) {
                byBedrock.put(saved.bedrockUuid(), saved);
                byJava.put(saved.javaUuid(), saved);
            }
            return true;
        } catch (IOException exception) {
            healthy = false;
            logger.severe("Could not safely update approved-links.json; Bedrock access is now fail closed.");
            return false;
        }
    }

    private void loadExisting() throws IOException {
        JsonNode root = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
        if (root == null || !root.isObject() || root.path("schemaVersion").asInt(-1) != SCHEMA_VERSION
                || !root.path("links").isArray()) {
            throw new IOException("unsupported registry schema");
        }

        Map<UUID, ApprovedLink> loadedByBedrock = new HashMap<>();
        Map<UUID, ApprovedLink> loadedByJava = new HashMap<>();
        for (JsonNode node : root.path("links")) {
            if (!node.isObject()) {
                throw new IOException("registry link is not an object");
            }
            ApprovedLink link = new ApprovedLink(
                    UuidUtil.parse(requiredText(node, "bedrockUuid")),
                    requiredText(node, "xuid"),
                    requiredText(node, "gamertag"),
                    UuidUtil.parse(requiredText(node, "javaUuid")),
                    requiredText(node, "javaUsername"),
                    Instant.parse(requiredText(node, "linkedAt")));
            if (loadedByBedrock.put(link.bedrockUuid(), link) != null
                    || loadedByJava.put(link.javaUuid(), link) != null) {
                throw new IOException("registry contains a non one-to-one mapping");
            }
        }

        byBedrock.clear();
        byJava.clear();
        byBedrock.putAll(loadedByBedrock);
        byJava.putAll(loadedByJava);
        healthy = true;
    }

    private void persist(Collection<ApprovedLink> links) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        ArrayNode array = root.putArray("links");
        links.stream()
                .sorted(Comparator.comparing(link -> link.bedrockUuid().toString()))
                .forEach(link -> {
                    ObjectNode node = array.addObject();
                    node.put("bedrockUuid", UuidUtil.canonical(link.bedrockUuid()));
                    node.put("xuid", link.xuid());
                    node.put("gamertag", link.gamertag());
                    node.put("javaUuid", UuidUtil.canonical(link.javaUuid()));
                    node.put("javaUsername", link.javaUsername());
                    node.put("linkedAt", link.linkedAt().toString());
                });
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        AtomicFileWriter.write(file, bytes);
    }

    private static String requiredText(JsonNode node, String field) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IOException("registry field is missing: " + field);
        }
        return value.textValue();
    }
}
