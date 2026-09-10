package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.util.LimitedHttpResponse;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LimitedResponseTest {
    @Test
    void chunkedOversizeAndStalledBodyAreBoundedAndNextRequestSucceeds() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/large", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write(new byte[8192]);
            } finally { exchange.close(); }
        });
        server.createContext("/slow", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write(1);
                exchange.getResponseBody().flush();
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, 1);
            exchange.getResponseBody().write(1);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThrows(LimitedHttpResponse.ResponseTooLargeException.class,
                    () -> LimitedHttpResponse.send(client, HttpRequest.newBuilder(URI.create(base + "/large"))
                            .timeout(Duration.ofSeconds(2)).build(), 1024));
            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThrows(HttpTimeoutException.class,
                            () -> LimitedHttpResponse.send(client, HttpRequest.newBuilder(URI.create(base + "/slow"))
                                    .timeout(Duration.ofMillis(300)).build(), 1024)));
            assertEquals(200, LimitedHttpResponse.send(client, HttpRequest.newBuilder(URI.create(base + "/ok"))
                    .timeout(Duration.ofSeconds(2)).build(), 1024).statusCode());
        } finally {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
