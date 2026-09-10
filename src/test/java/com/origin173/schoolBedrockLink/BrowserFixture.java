package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.config.*;
import com.origin173.schoolBedrockLink.http.*;
import com.origin173.schoolBedrockLink.link.*;
import com.origin173.schoolBedrockLink.oauth.*;
import com.origin173.schoolBedrockLink.security.*;
import com.origin173.schoolBedrockLink.audit.AuditLogger;
import com.origin173.schoolBedrockLink.floodgate.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Local-only browser fixture using production handlers and a simulated provider/Floodgate. */
public final class BrowserFixture {
    public static void main(String[] args) throws Exception {
        var logger = Logger.getAnonymousLogger();
        var provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 18794), 0);
        var app = HttpServer.create(new InetSocketAddress("127.0.0.1", 18793), 0);
        String origin = "http://127.0.0.1:18794";
        OAuthConfig oauth = new OAuthConfig("blessing-skin", URI.create(origin + "/authorize"),
                URI.create(origin + "/token"), URI.create(origin + "/user"), URI.create(origin + "/players"),
                URI.create(origin + "/profiles"), "fixture", "fixture-secret", List.of(), true);
        var config = new PluginConfig(new HttpConfig("127.0.0.1", 18793, "http://127.0.0.1:18793/auth", 2),
                oauth, new BindingConfig(10, 300, true, true), new RateLimitConfig(true, 100, 5),
                new MessageConfig("", "", "", "Not ready"), true);
        var pending = new PendingLinkService(new SecureTokenGenerator());
        var sessions = new OAuthSessionService(new SecureTokenGenerator());
        var directory = Files.createTempDirectory("school-link-browser-");
        var registry = new ApprovedLinkRegistry(directory, logger);
        var mappings = new ConcurrentHashMap<UUID, FloodgateMapping>();
        LinkOperations floodgate = new LinkOperations() {
            public boolean isReady() { return true; }
            public MappingLookup getMapping(UUID id, Duration timeout) { return MappingLookup.available(mappings.get(id)); }
            public boolean linkPlayer(UUID id, UUID java, String name, Duration timeout) {
                mappings.put(id, new FloodgateMapping(java, name)); return true;
            }
            public boolean unlinkPlayer(UUID id, Duration timeout) {
                mappings.entrySet().removeIf(e -> e.getValue().javaUuid().equals(id)); return true;
            }
        };
        var links = new LinkService(registry, floodgate, new AuditLogger(directory, logger), logger, Duration.ofSeconds(2));
        var context = new WebContext(() -> config, pending, sessions, new OAuthService(oauth, Duration.ofSeconds(2)),
                new BlessingSkinProfileService(Duration.ofSeconds(2), logger), links, registry, null,
                new RateLimiter(100, Duration.ofMinutes(1)), new RateLimiter(5, Duration.ofMinutes(5)),
                () -> true, logger);
        app.createContext("/auth/start", new StartHandler(context));
        app.createContext("/auth/oauth/callback", new OAuthCallbackHandler(context));
        app.createContext("/auth/confirm", new ConfirmHandler(context));
        app.createContext("/", new RootHandler(context));
        provider.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/authorize")) {
                HttpUtil.redirect(exchange, origin + "/login?" + exchange.getRequestURI().getRawQuery());
            } else if (path.equals("/login")) {
                Map<String, String> params;
                try { params = HttpUtil.query(exchange); }
                catch (HttpUtil.BadRequestException failure) { throw new java.io.IOException(failure); }
                String callback = params.get("redirect_uri") + "?state=" + params.get("state") + "&code=fixture";
                HttpUtil.html(exchange, 200, "<h1>Simulated Blessing Skin login</h1><a href=\""
                        + HtmlEscaper.escape(callback) + "\">Authorize fixture account</a>");
            } else {
                String json = switch (path) {
                    case "/token" -> "{\"access_token\":\"fixture-token\"}";
                    case "/user" -> "{}";
                    case "/players" -> "[{\"name\":\"PlayerA\"}]";
                    case "/profiles" -> "[{\"name\":\"PlayerA\",\"id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}]";
                    default -> "{}";
                };
                HttpUtil.json(exchange, 200, json);
            }
            exchange.close();
        });
        provider.start();
        app.start();
        var code = pending.getOrCreate(new BedrockIdentity(UUID.randomUUID(), "1234", "Fixture"), 10, 300, true, Instant.now());
        System.out.println("FIXTURE http://127.0.0.1:18793/auth/ CODE " + code.bindingCode());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { app.stop(0); provider.stop(0); }));
    }
}
