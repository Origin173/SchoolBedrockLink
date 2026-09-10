package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.http.RemoteAdmissionHandler;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RemoteAdmissionTest {
    @Test
    void slowUpstreamConsumesFourWorkersWithoutAdmission() throws Exception { run(false); }
    @Test
    void boundedRemoteConcurrencyKeepsHomeResponsive() throws Exception { run(true); }

    private void run(boolean limited) throws Exception {
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        HttpServer app = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var workers = Executors.newFixedThreadPool(4);
        var upstreamWorkers = Executors.newCachedThreadPool();
        CountDownLatch entered = new CountDownLatch(limited ? 2 : 4);
        CountDownLatch release = new CountDownLatch(1);
        upstream.setExecutor(upstreamWorkers);
        app.setExecutor(workers);
        upstream.createContext("/", exchange -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            try { exchange.sendResponseHeaders(200, -1); } finally { exchange.close(); }
        });
        upstream.start();
        try (HttpClient client = HttpClient.newHttpClient()) {
            URI remote = URI.create("http://127.0.0.1:" + upstream.getAddress().getPort());
            HttpHandler slow = exchange -> {
                try {
                    client.send(HttpRequest.newBuilder(remote).timeout(Duration.ofSeconds(4)).build(),
                            HttpResponse.BodyHandlers.discarding());
                    exchange.sendResponseHeaders(200, -1);
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                finally { exchange.close(); }
            };
            app.createContext("/slow", limited ? new RemoteAdmissionHandler(new Semaphore(2), slow) : slow);
            app.createContext("/", exchange -> { exchange.sendResponseHeaders(200, -1); exchange.close(); });
            app.start();
            URI base = URI.create("http://127.0.0.1:" + app.getAddress().getPort());
            for (int i = 0; i < (limited ? 2 : 4); i++) {
                client.sendAsync(HttpRequest.newBuilder(base.resolve("/slow")).build(), HttpResponse.BodyHandlers.discarding());
            }
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                if (limited) {
                    assertEquals(503, client.send(HttpRequest.newBuilder(base.resolve("/slow"))
                                    .timeout(Duration.ofSeconds(1)).build(),
                            HttpResponse.BodyHandlers.discarding()).statusCode());
                    assertEquals(200, client.send(HttpRequest.newBuilder(base).timeout(Duration.ofSeconds(1)).build(),
                            HttpResponse.BodyHandlers.discarding()).statusCode());
                } else {
                    assertThrows(HttpTimeoutException.class, () -> client.send(HttpRequest.newBuilder(base)
                            .timeout(Duration.ofMillis(300)).build(), HttpResponse.BodyHandlers.discarding()));
                }
            } finally { release.countDown(); }
        } finally {
            release.countDown();
            app.stop(0);
            upstream.stop(0);
            workers.shutdownNow();
            upstreamWorkers.shutdownNow();
        }
    }
}
