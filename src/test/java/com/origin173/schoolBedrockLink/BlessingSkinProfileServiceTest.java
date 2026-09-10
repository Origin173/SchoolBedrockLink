package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.config.OAuthConfig;
import com.origin173.schoolBedrockLink.oauth.BlessingSkinProfileService;
import com.origin173.schoolBedrockLink.oauth.MinecraftProfile;
import com.origin173.schoolBedrockLink.oauth.OAuthToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlessingSkinProfileServiceTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger yggdrasilRequests = new AtomicInteger();
    private final AtomicReference<String> yggdrasilBody = new AtomicReference<>();
    private final AtomicReference<String> userAuthorization = new AtomicReference<>();
    private final AtomicReference<String> playersAuthorization = new AtomicReference<>();
    private final AtomicReference<String> yggdrasilAuthorization = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleRequest);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void onlyNamesFromCurrentUsersPlayersListSurviveYggdrasilResponse() {
        BlessingSkinProfileService service = new BlessingSkinProfileService(
                Duration.ofSeconds(3), Logger.getAnonymousLogger());

        List<MinecraftProfile> profiles = service.fetchProfiles(
                new OAuthToken("test-token"), config());

        assertEquals(1, profiles.size());
        assertEquals("PlayerA", profiles.get(0).username());
        assertEquals(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), profiles.get(0).uuid());
        assertEquals(1, yggdrasilRequests.get());
        assertEquals("[\"PlayerA\"]", yggdrasilBody.get());
        assertEquals("Bearer test-token", userAuthorization.get());
        assertEquals("Bearer test-token", playersAuthorization.get());
        assertNull(yggdrasilAuthorization.get());
    }

    @Test
    void emptyPlayersListProducesNoProfilesAndDoesNotQueryYggdrasil() {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            if ("/api/user".equals(exchange.getRequestURI().getPath())) {
                respond(exchange, 200, "{}");
            } else if ("/api/players".equals(exchange.getRequestURI().getPath())) {
                respond(exchange, 200, "[]");
            } else {
                respond(exchange, 404, "{}");
            }
        });

        BlessingSkinProfileService service = new BlessingSkinProfileService(
                Duration.ofSeconds(3), Logger.getAnonymousLogger());

        assertTrue(service.fetchProfiles(new OAuthToken("test-token"), config()).isEmpty());
        assertEquals(0, yggdrasilRequests.get());
        assertNull(yggdrasilBody.get());
    }

    @Test
    void wrappedCodeDataPlayersResponseIsAccepted() {
        respondPlayersWith("{\"code\":0,\"data\":[{\"pid\":10,\"uid\":3,\"name\":\"PlayerA\"}]}");

        BlessingSkinProfileService service = new BlessingSkinProfileService(
                Duration.ofSeconds(3), Logger.getAnonymousLogger());

        List<MinecraftProfile> profiles = service.fetchProfiles(
                new OAuthToken("test-token"), config());
        assertEquals(1, profiles.size());
        assertEquals("PlayerA", profiles.get(0).username());
    }

    @Test
    void wrappedDataOnlyPlayersResponseIsAccepted() {
        respondPlayersWith("{\"data\":[{\"pid\":10,\"uid\":3,\"name\":\"PlayerA\"}]}");

        BlessingSkinProfileService service = new BlessingSkinProfileService(
                Duration.ofSeconds(3), Logger.getAnonymousLogger());

        assertEquals(1, service.fetchProfiles(new OAuthToken("test-token"), config()).size());
    }

    @Test
    void wrappedNonZeroCodePlayersResponseIsRejected() {
        assertPlayersRejectedWith("{\"code\":1,\"message\":\"forbidden\",\"data\":[]}",
                "fields=code,message,data");
    }

    @Test
    void errorEnvelopeDiagnosticsIncludeUpstreamCodeAndMessage() {
        assertPlayersRejectedWith(
                "{\"code\":401,\"message\":\"Invalid scope(s) provided.\"}",
                "upstreamCode=401 upstreamMessage='Invalid scope(s) provided.'");
    }

    @Test
    void playersFieldWrapperIsAccepted() {
        respondPlayersWith("{\"players\":[{\"pid\":10,\"uid\":3,\"name\":\"PlayerA\"}]}");

        BlessingSkinProfileService service = new BlessingSkinProfileService(
                Duration.ofSeconds(3), Logger.getAnonymousLogger());

        assertEquals(1, service.fetchProfiles(new OAuthToken("test-token"), config()).size());
    }

    @Test
    void barePlayerNameArrayIsAccepted() {
        respondPlayersWith("[\"PlayerA\"]");

        BlessingSkinProfileService service = new BlessingSkinProfileService(
                Duration.ofSeconds(3), Logger.getAnonymousLogger());

        List<MinecraftProfile> profiles = service.fetchProfiles(
                new OAuthToken("test-token"), config());
        assertEquals(1, profiles.size());
        assertEquals("PlayerA", profiles.get(0).username());
    }

    @Test
    void yggdrasilConnectUserInfoShapeIsAccepted() {
        respondPlayersWith("{\"sub\":\"3\",\"nickname\":\"someone\",\"availableProfiles\":["
                + "{\"id\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"name\":\"PlayerA\"}]}");

        BlessingSkinProfileService service = new BlessingSkinProfileService(
                Duration.ofSeconds(3), Logger.getAnonymousLogger());

        List<MinecraftProfile> profiles = service.fetchProfiles(
                new OAuthToken("test-token"), config());
        assertEquals(1, profiles.size());
        assertEquals("PlayerA", profiles.get(0).username());
        assertEquals(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), profiles.get(0).uuid());
    }

    @Test
    void objectPlayersResponseIsRejectedWithShapeDiagnostic() {
        assertPlayersRejectedWith("{\"uid\":3,\"nickname\":\"someone\"}",
                "object(fields=uid,nickname)");
    }

    @Test
    void playerEntryMissingNameReportsIndexAndFields() {
        assertPlayersRejectedWith("[{\"pid\":10,\"uid\":3}]",
                "index 0 has no name field; entry fields=pid,uid");
    }

    @Test
    void yggdrasilWrappedResponseIsAccepted() {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/api/user".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"uid\":3}");
            } else if ("/api/players".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "[{\"pid\":10,\"uid\":3,\"name\":\"PlayerA\"}]");
            } else if ("/api/yggdrasil/api/profiles/minecraft".equals(path)
                    && "POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"code\":0,\"data\":["
                        + "{\"id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"name\":\"PlayerA\"}]}");
            } else {
                respond(exchange, 404, "{}");
            }
        });

        BlessingSkinProfileService service = new BlessingSkinProfileService(
                Duration.ofSeconds(3), Logger.getAnonymousLogger());

        List<MinecraftProfile> profiles = service.fetchProfiles(
                new OAuthToken("test-token"), config());
        assertEquals(1, profiles.size());
        assertEquals("PlayerA", profiles.get(0).username());
    }

    private void assertPlayersRejectedWith(String playersBody, String expectedDiagnostic) {
        respondPlayersWith(playersBody);

        BlessingSkinProfileService service = new BlessingSkinProfileService(
                Duration.ofSeconds(3), Logger.getAnonymousLogger());

        BlessingSkinProfileService.ProfileException exception = assertThrows(
                BlessingSkinProfileService.ProfileException.class,
                () -> service.fetchProfiles(new OAuthToken("test-token"), config()));
        assertEquals(BlessingSkinProfileService.Kind.PLAYERS_RESPONSE_INVALID, exception.kind());
        assertTrue(exception.getMessage().contains(expectedDiagnostic),
                "diagnostic should contain '" + expectedDiagnostic + "' but was: " + exception.getMessage());
    }

    private void respondPlayersWith(String playersBody) {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/api/user".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"uid\":3}");
            } else if ("/api/players".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, playersBody);
            } else if ("/api/yggdrasil/api/profiles/minecraft".equals(path)
                    && "POST".equals(exchange.getRequestMethod())) {
                handleRequest(exchange);
            } else {
                respond(exchange, 404, "{}");
            }
        });
    }

    private OAuthConfig config() {
        return new OAuthConfig("blessing-skin", endpoint("/authorize"), endpoint("/token"),
                endpoint("/api/user"), endpoint("/api/players"),
                endpoint("/api/yggdrasil/api/profiles/minecraft"),
                "client", "secret", List.of(), true);
    }

    private URI endpoint(String path) {
        return URI.create(baseUrl + path);
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/api/user".equals(path) && "GET".equals(exchange.getRequestMethod())) {
            userAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"uid\":3}");
        } else if ("/api/players".equals(path) && "GET".equals(exchange.getRequestMethod())) {
            playersAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "[{\"pid\":10,\"uid\":3,\"name\":\"PlayerA\"}]");
        } else if ("/api/yggdrasil/api/profiles/minecraft".equals(path)
                && "POST".equals(exchange.getRequestMethod())) {
            yggdrasilRequests.incrementAndGet();
            yggdrasilBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            yggdrasilAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "[{\"id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"name\":\"PlayerA\"},"
                    + "{\"id\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\"name\":\"Admin\"}]");
        } else {
            respond(exchange, 404, "{}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
