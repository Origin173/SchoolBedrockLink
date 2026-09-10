package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.config.*;
import com.origin173.schoolBedrockLink.floodgate.BedrockIdentity;
import com.origin173.schoolBedrockLink.http.*;
import com.origin173.schoolBedrockLink.link.*;
import com.origin173.schoolBedrockLink.oauth.*;
import com.origin173.schoolBedrockLink.security.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpFlowTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test
    void realHttpRedirectCallbackReplayAndConfirmationValidation() throws Exception {
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        HttpServer app = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var logger = Logger.getAnonymousLogger();
        var tokenStatus = new java.util.concurrent.atomic.AtomicInteger(200);
        var tokenRequests = new java.util.concurrent.atomic.AtomicInteger();
        String origin = "http://127.0.0.1:" + provider.getAddress().getPort();
        String publicUrl = "http://127.0.0.1:" + app.getAddress().getPort();
        provider.createContext("/", exchange -> {
            String body = switch (exchange.getRequestURI().getPath()) {
                case "/token" -> "{\"access_token\":\"fixture-token\"}";
                case "/user" -> "{}";
                case "/players" -> "[{\"name\":\"PlayerA\"}]";
                case "/profiles" -> "[{\"name\":\"PlayerA\",\"id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}]";
                default -> "<h1>Provider login</h1>";
            };
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            boolean tokenRequest = exchange.getRequestURI().getPath().equals("/token");
            if (tokenRequest) tokenRequests.incrementAndGet();
            exchange.sendResponseHeaders(tokenRequest ? tokenStatus.get() : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        OAuthConfig oauthConfig = new OAuthConfig("blessing-skin", URI.create(origin + "/authorize"),
                URI.create(origin + "/token"), URI.create(origin + "/user"), URI.create(origin + "/players"),
                URI.create(origin + "/profiles"), "client", "fixture-secret", List.of(), true);
        PluginConfig config = new PluginConfig(new HttpConfig("127.0.0.1", app.getAddress().getPort(),
                publicUrl + "/auth", 2), oauthConfig, new BindingConfig(10, 300, true, true),
                new RateLimitConfig(true, 100, 5), new MessageConfig("", "", "", ""), true);
        var current = new AtomicReference<>(config);
        var pending = new PendingLinkService(new SecureTokenGenerator());
        var sessions = new OAuthSessionService(new SecureTokenGenerator());
        var registry = new ApprovedLinkRegistry(directory, logger);
        var mappings = new java.util.concurrent.ConcurrentHashMap<UUID, com.origin173.schoolBedrockLink.floodgate.FloodgateMapping>();
        var fake = new com.origin173.schoolBedrockLink.floodgate.LinkOperations() {
            public boolean isReady() { return true; }
            public com.origin173.schoolBedrockLink.floodgate.MappingLookup getMapping(UUID id, Duration timeout) {
                return com.origin173.schoolBedrockLink.floodgate.MappingLookup.available(mappings.get(id));
            }
            public boolean linkPlayer(UUID id, UUID java, String name, Duration timeout) {
                mappings.put(id, new com.origin173.schoolBedrockLink.floodgate.FloodgateMapping(java, name)); return true;
            }
            public boolean unlinkPlayer(UUID id, Duration timeout) { return false; }
        };
        var links = new LinkService(registry, fake,
                new com.origin173.schoolBedrockLink.audit.AuditLogger(directory, logger), logger, Duration.ofSeconds(2));
        var context = new WebContext(current::get, pending, sessions,
                new OAuthService(oauthConfig, Duration.ofSeconds(2)),
                new BlessingSkinProfileService(Duration.ofSeconds(2), logger), links, registry, null,
                new RateLimiter(100, Duration.ofMinutes(1)), new RateLimiter(5, Duration.ofMinutes(5)),
                () -> true, logger);
        app.createContext("/auth/start", new StartHandler(context));
        app.createContext("/auth/oauth/callback", new OAuthCallbackHandler(context));
        app.createContext("/auth/confirm", new ConfirmHandler(context));
        app.createContext("/", new RootHandler(context));
        provider.start();
        app.start();
        try (HttpClient client = HttpClient.newHttpClient()) {
            var code = pending.getOrCreate(new BedrockIdentity(UUID.randomUUID(), "1234", "PlayerA"),
                    10, 300, true, Instant.now());
            var home = client.send(HttpRequest.newBuilder(URI.create(publicUrl + "/auth/")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, home.statusCode());
            assertTrue(home.body().contains("action=\"/auth/start\""));
            assertTrue(home.headers().firstValue("Content-Security-Policy").orElseThrow().contains(origin));
            URI start = URI.create(publicUrl + "/auth/start?code=" + code.bindingCode());
            var redirect = client.send(HttpRequest.newBuilder(start).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(302, redirect.statusCode());
            URI location = URI.create(redirect.headers().firstValue("Location").orElseThrow());
            var params = HttpUtil.parseParameters(location.getRawQuery(), 4096);
            assertEquals(publicUrl + "/auth/oauth/callback", params.get("redirect_uri"));
            assertEquals("S256", params.get("code_challenge_method"));
            assertTrue(redirect.headers().firstValue("Content-Security-Policy").orElseThrow().contains(origin));
            // Reloaded provider differs. This in-flight session must still use its old snapshot.
            var badOAuth = new OAuthConfig("blessing-skin", URI.create(origin + "/authorize"),
                    URI.create("http://127.0.0.1:1/token"), URI.create(origin + "/user"),
                    URI.create(origin + "/players"), URI.create(origin + "/profiles"),
                    "new-client", "new-secret", List.of(), true);
            current.set(new PluginConfig(config.http(), badOAuth, config.binding(), config.rateLimit(),
                    config.messages(), true));
            URI callback = URI.create(publicUrl + "/auth/oauth/callback?state=" + params.get("state") + "&code=fixture");
            var selection = client.send(HttpRequest.newBuilder(callback).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, selection.statusCode());
            assertTrue(selection.body().contains("action=\"/auth/confirm\""));
            assertTrue(selection.body().contains("PlayerA"));
            assertEquals(400, client.send(HttpRequest.newBuilder(callback).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
            var matcher = java.util.regex.Pattern.compile("name=\"confirmationToken\" value=\"([^\"]+)\"")
                    .matcher(selection.body());
            assertTrue(matcher.find());
            var invalidIndex = HttpRequest.newBuilder(URI.create(publicUrl + "/auth/confirm"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("confirmationToken=" + matcher.group(1) + "&profileIndex=99"))
                    .build();
            assertEquals(400, client.send(invalidIndex, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(1, sessions.confirmationCount());
            for (int i = 0; i < 4; i++) client.send(HttpRequest.newBuilder(start).build(), HttpResponse.BodyHandlers.ofString());
            var limited = client.send(HttpRequest.newBuilder(start).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(429, limited.statusCode());
            assertTrue(pending.find(code.bindingCode(), Instant.now()).isPresent());
            var validConfirmation = HttpRequest.newBuilder(URI.create(publicUrl + "/auth/confirm"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("confirmationToken=" + matcher.group(1) + "&profileIndex=0"))
                    .build();
            assertEquals(200, client.send(validConfirmation, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertFalse(pending.find(code.bindingCode(), Instant.now()).isPresent());
            assertEquals(1, registry.snapshot().size());
            assertEquals(400, client.send(validConfirmation, HttpResponse.BodyHandlers.ofString()).statusCode());

            var newCode = pending.getOrCreate(new BedrockIdentity(UUID.randomUUID(), "5678", "Another"),
                    10, 300, true, Instant.now());
            var expired = sessions.createAuthorization(newCode, Instant.now().minusSeconds(10), Duration.ofSeconds(1), config);
            int previousRequests = tokenRequests.get();
            var expiredCallback = HttpRequest.newBuilder(URI.create(publicUrl + "/auth/oauth/callback?state="
                    + expired.state() + "&code=fixture")).build();
            assertEquals(400, client.send(expiredCallback, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(previousRequests, tokenRequests.get());
            tokenStatus.set(401);
            var failureSession = sessions.createAuthorization(newCode, Instant.now(), Duration.ofSeconds(300), config);
            var failureCallback = HttpRequest.newBuilder(URI.create(publicUrl + "/auth/oauth/callback?state="
                    + failureSession.state() + "&code=fixture")).build();
            var failure = client.send(failureCallback, HttpResponse.BodyHandlers.ofString());
            assertEquals(502, failure.statusCode());
            assertTrue(failure.body().contains("错误编号"));
            assertFalse(failure.body().contains("fixture-secret"));
        } finally {
            app.stop(0);
            provider.stop(0);
        }
    }
}
