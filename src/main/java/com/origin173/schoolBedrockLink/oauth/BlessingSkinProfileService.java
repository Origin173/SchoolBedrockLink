package com.origin173.schoolBedrockLink.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.origin173.schoolBedrockLink.config.OAuthConfig;
import com.origin173.schoolBedrockLink.util.LimitedHttpResponse;
import com.origin173.schoolBedrockLink.util.UuidUtil;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/** Blessing Skin-specific user, player and Yggdrasil profile integration. */
public final class BlessingSkinProfileService {

    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final int MAX_PLAYERS = 32;

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Logger logger;

    public BlessingSkinProfileService(Duration requestTimeout, Logger logger) {
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Verifies the bearer token, obtains the user's player names, and resolves
     * only those names through Blessing Skin's Yggdrasil endpoint.
     */
    public List<MinecraftProfile> fetchProfiles(OAuthToken token, OAuthConfig config)
            throws ProfileException {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(config, "config");

        JsonNode user = getJson(config.userEndpoint(), token, Endpoint.USER);
        if (!user.isObject()) {
            throw new ProfileException(Kind.USER_RESPONSE_INVALID, "Blessing Skin /api/user returned invalid JSON");
        }

        JsonNode players = getJson(config.playersEndpoint(), token, Endpoint.PLAYERS);
        List<String> allowedNames = parseAllowedNames(unwrapDataArray(players, Kind.PLAYERS_RESPONSE_INVALID,
                "Blessing Skin /api/players"));
        if (allowedNames.isEmpty()) {
            return List.of();
        }

        JsonNode yggdrasilProfiles = lookupYggdrasilProfiles(config.yggdrasilProfilesEndpoint(), allowedNames);
        return intersectProfiles(allowedNames, yggdrasilProfiles);
    }

    private JsonNode getJson(URI endpoint, OAuthToken token, Endpoint endpointKind) {
        if (endpoint == null) {
            throw new ProfileException(endpointKind == Endpoint.USER
                    ? Kind.USER_RESPONSE_INVALID : Kind.PLAYERS_RESPONSE_INVALID,
                    "Blessing Skin endpoint is not configured");
        }
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token.accessToken())
                .GET()
                .build();
        LimitedHttpResponse.Response response = send(request);
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ProfileException(endpointKind == Endpoint.USER
                    ? Kind.USER_UNAUTHORIZED : Kind.PLAYERS_UNAUTHORIZED,
                    "Blessing Skin rejected the bearer token", response.statusCode());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ProfileException(endpointKind == Endpoint.USER
                    ? Kind.USER_RESPONSE_INVALID : Kind.PLAYERS_RESPONSE_INVALID,
                    "Blessing Skin endpoint rejected the request", response.statusCode());
        }
        return readJson(response.body(), endpointKind == Endpoint.USER
                ? Kind.USER_RESPONSE_INVALID : Kind.PLAYERS_RESPONSE_INVALID);
    }

    /**
     * Blessing Skin core returns a bare JSON array, but other deployments wrap it
     * or expose it through a different envelope: {"code":0,"data":[...]},
     * {"players":[...]}, or the Yggdrasil Connect userinfo shape
     * {"sub":"1","availableProfiles":[{"id":"<uuid>","name":"<player>"}]}.
     * Accept all of those; anything else is rejected with a shape diagnostic.
     */
    private JsonNode unwrapDataArray(JsonNode payload, Kind kind, String endpoint) {
        if (payload.isArray()) {
            return payload;
        }
        if (payload.isObject()) {
            JsonNode data = payload.get("data");
            if (data == null) {
                data = payload.get("players");
            }
            if (data == null) {
                data = payload.get("availableProfiles");
            }
            if (data != null && data.isArray()) {
                JsonNode code = payload.get("code");
                if (code == null || !code.isNumber() || code.intValue() == 0) {
                    return data;
                }
            }
        }
        throw new ProfileException(kind,
                endpoint + " returned " + describeShape(payload) + describeEnvelope(payload));
    }

    /**
     * Reports an upstream error envelope (code/message) so that a rejected call is
     * diagnosable. Only short, sanitized error text is used; credentials in URL form
     * are redacted even though this text comes from the configured school endpoint.
     */
    private static String describeEnvelope(JsonNode payload) {
        if (!payload.isObject()) {
            return "";
        }
        StringBuilder detail = new StringBuilder();
        JsonNode code = payload.get("code");
        if (code != null && code.isNumber()) {
            detail.append(" upstreamCode=").append(code.intValue());
        }
        JsonNode error = payload.get("error");
        if (error != null && error.isTextual()) {
            detail.append(" upstreamError=").append(redact(error.textValue()));
        }
        JsonNode message = payload.get("message");
        if (message != null && message.isTextual()) {
            detail.append(" upstreamMessage=").append(redact(message.textValue()));
        }
        return detail.toString();
    }

    private static String redact(String value) {
        String cleaned = value
                .replaceAll("(?i)([?&](?:access_token|token|code|client_secret|secret|state)=)[^&\\s]*",
                        "$1<redacted>")
                .replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() > 120) {
            cleaned = cleaned.substring(0, 120) + "...";
        }
        return "'" + cleaned + "'";
    }

    /** Describes JSON structure only — never field values — for safe logging. */
    private static String describeShape(JsonNode payload) {
        if (payload == null) {
            return "null";
        }
        if (payload.isArray()) {
            return "array(size=" + payload.size() + ")";
        }
        if (payload.isObject()) {
            return "object(fields=" + joinFieldNames(payload) + ")";
        }
        return payload.getNodeType().toString().toLowerCase(java.util.Locale.ROOT);
    }

    private List<String> parseAllowedNames(JsonNode players) {
        if (!players.isArray()) {
            throw new ProfileException(Kind.PLAYERS_RESPONSE_INVALID,
                    "Blessing Skin /api/players did not return an array");
        }
        if (players.size() > MAX_PLAYERS) {
            throw new ProfileException(Kind.PLAYERS_RESPONSE_INVALID,
                    "Blessing Skin returned too many players");
        }

        Set<String> uniqueNames = new LinkedHashSet<>();
        for (int index = 0; index < players.size(); index++) {
            JsonNode player = players.get(index);
            String name;
            if (player.isTextual()) {
                // Some deployments list bare player names instead of player objects.
                name = player.textValue();
            } else if (player.isObject()) {
                JsonNode nameNode = player.get("name");
                if (nameNode == null || !nameNode.isTextual()) {
                    throw new ProfileException(Kind.PLAYERS_RESPONSE_INVALID,
                            "Blessing Skin player entry at index " + index + " has no name field; entry fields="
                                    + joinFieldNames(player));
                }
                name = nameNode.textValue();
            } else {
                throw new ProfileException(Kind.PLAYERS_RESPONSE_INVALID,
                        "Blessing Skin player entry at index " + index + " is "
                                + describeShape(player) + ", expected object or string");
            }
            if (name == null || name.isBlank() || name.length() > 64 || !name.equals(name.trim())) {
                throw new ProfileException(Kind.PLAYERS_RESPONSE_INVALID,
                        "Blessing Skin player name is invalid");
            }
            uniqueNames.add(name);
        }
        return List.copyOf(uniqueNames);
    }

    private JsonNode lookupYggdrasilProfiles(URI endpoint, List<String> allowedNames) {
        if (endpoint == null) {
            throw new ProfileException(Kind.YGGDRASIL_RESPONSE_INVALID,
                    "Blessing Skin Yggdrasil endpoint is not configured");
        }
        byte[] requestBody;
        try {
            requestBody = mapper.writeValueAsBytes(allowedNames);
        } catch (IOException exception) {
            throw new ProfileException(Kind.YGGDRASIL_RESPONSE_INVALID,
                    "Could not create Yggdrasil profile request", exception);
        }

        // The Yggdrasil lookup is public and is intentionally not sent the
        // OAuth bearer token. The request body is restricted to /api/players names.
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();
        LimitedHttpResponse.Response response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ProfileException(Kind.YGGDRASIL_RESPONSE_INVALID,
                    "Blessing Skin Yggdrasil endpoint rejected the request", response.statusCode());
        }
        return unwrapDataArray(readJson(response.body(), Kind.YGGDRASIL_RESPONSE_INVALID),
                Kind.YGGDRASIL_RESPONSE_INVALID, "Blessing Skin Yggdrasil response");
    }

    private List<MinecraftProfile> intersectProfiles(List<String> allowedNames, JsonNode response) {
        if (!response.isArray()) {
            throw new ProfileException(Kind.YGGDRASIL_RESPONSE_INVALID,
                    "Blessing Skin Yggdrasil response did not return an array");
        }
        if (response.size() > MAX_PLAYERS * 2) {
            throw new ProfileException(Kind.YGGDRASIL_RESPONSE_INVALID,
                    "Blessing Skin Yggdrasil returned too many profiles");
        }

        Map<String, MinecraftProfile> byName = new LinkedHashMap<>();
        for (JsonNode node : response) {
            if (!node.isObject()) {
                continue;
            }
            JsonNode nameNode = node.get("name");
            if (nameNode == null || !nameNode.isTextual()) {
                continue;
            }
            String name = nameNode.textValue();
            if (!allowedNames.contains(name)) {
                // An unexpected profile such as "Admin" is never accepted.
                continue;
            }
            if (byName.containsKey(name)) {
                throw new ProfileException(Kind.YGGDRASIL_RESPONSE_INVALID,
                        "Blessing Skin Yggdrasil returned a duplicate allowed profile name");
            }
            JsonNode idNode = node.get("id");
            if (idNode == null || !idNode.isTextual()) {
                logMissing(name, "missing UUID");
                continue;
            }
            UUID uuid;
            try {
                uuid = UuidUtil.parse(idNode.textValue());
            } catch (IllegalArgumentException exception) {
                logMissing(name, "invalid UUID");
                continue;
            }
            try {
                byName.put(name, new MinecraftProfile(uuid, name));
            } catch (IllegalArgumentException exception) {
                logMissing(name, "invalid profile name");
            }
        }

        List<MinecraftProfile> result = new ArrayList<>();
        for (String allowedName : allowedNames) {
            MinecraftProfile profile = byName.get(allowedName);
            if (profile == null) {
                logMissing(allowedName, "not returned by Yggdrasil");
            } else {
                result.add(profile);
            }
        }
        if (result.isEmpty()) {
            throw new ProfileException(Kind.YGGDRASIL_EMPTY,
                    "Blessing Skin Yggdrasil returned no allowed profiles");
        }
        return List.copyOf(result);
    }

    private static String joinFieldNames(JsonNode object) {
        List<String> fieldNames = new ArrayList<>();
        object.fieldNames().forEachRemaining(fieldNames::add);
        return String.join(",", fieldNames);
    }

    private void logMissing(String name, String reason) {
        logger.warning("Blessing Skin Yggdrasil profile unavailable for player '"
                + safeForLog(name) + "': " + reason);
    }

    private JsonNode readJson(byte[] bytes, Kind invalidKind) {
        if (bytes == null || bytes.length == 0) {
            throw new ProfileException(invalidKind, "Blessing Skin API returned an empty response");
        }
        try {
            JsonNode root = mapper.readTree(bytes);
            if (root == null) {
                throw new ProfileException(invalidKind, "Blessing Skin API returned empty JSON");
            }
            return root;
        } catch (IOException exception) {
            throw new ProfileException(invalidKind, "Blessing Skin API returned invalid JSON", exception);
        }
    }

    private LimitedHttpResponse.Response send(HttpRequest request) {
        try {
            return LimitedHttpResponse.send(httpClient, request, MAX_RESPONSE_BYTES);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProfileException(Kind.NETWORK, "Blessing Skin API request interrupted", exception);
        } catch (IOException exception) {
            throw new ProfileException(Kind.NETWORK, "Blessing Skin API request failed", exception);
        }
    }

    private static String safeForLog(String value) {
        return value.replaceAll("[\\p{Cntrl}]", "?");
    }

    private enum Endpoint {
        USER,
        PLAYERS
    }

    public enum Kind {
        USER_UNAUTHORIZED,
        USER_RESPONSE_INVALID,
        PLAYERS_UNAUTHORIZED,
        PLAYERS_RESPONSE_INVALID,
        YGGDRASIL_EMPTY,
        YGGDRASIL_RESPONSE_INVALID,
        NETWORK
    }

    public static final class ProfileException extends RuntimeException {
        private final Kind kind;
        private int upstreamStatus;
        public int upstreamStatus() { return upstreamStatus; }
        public ProfileException(Kind kind, String message, int upstreamStatus) {
            super(message);
            this.kind = kind;
            this.upstreamStatus = upstreamStatus;
        }

        public ProfileException(Kind kind, String message) {
            super(message);
            this.kind = kind;
        }

        public ProfileException(Kind kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }

        public Kind kind() {
            return kind;
        }
    }
}
