package com.origin173.schoolBedrockLink.http;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProxyHeaderTest {
    @Test
    void forgedHttpHeaderChainsCannotRotateRateLimitIdentity() throws Exception {
        var config = new com.origin173.schoolBedrockLink.config.PluginConfig(null, null, null,
                new com.origin173.schoolBedrockLink.config.RateLimitConfig(true, 1, 1), null, true);
        var context = new WebContext(() -> config, null, null, null, null, null, null, null,
                new com.origin173.schoolBedrockLink.security.RateLimiter(1, java.time.Duration.ofMinutes(1)),
                null, () -> true, java.util.logging.Logger.getAnonymousLogger());
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(context.allowRequest(exchange) ? 200 : 429, -1);
            exchange.close();
        });
        server.start();
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            var uri = java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            for (int i = 1; i <= 2; i++) {
                var request = java.net.http.HttpRequest.newBuilder(uri)
                        .header("X-Forwarded-For", "192.0.2." + i + ", 198.51.100.1").build();
                assertEquals(i == 1 ? 200 : 429,
                        client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsForgedChainsAndHostNamesAndCanonicalizesLiteralAddresses() {
        assertNull(WebContext.trustedForwardedClient("1.2.3.4, 198.51.100.1"));
        assertNull(WebContext.trustedForwardedClient("attacker.example"));
        assertNull(WebContext.trustedForwardedClient("198.51.100.256"));
        assertEquals("198.51.100.1", WebContext.trustedForwardedClient(" 198.51.100.1 "));
        assertEquals(WebContext.trustedForwardedClient("2001:db8::1"),
                WebContext.trustedForwardedClient("2001:0db8:0:0:0:0:0:1"));
    }
}
