package com.origin173.schoolBedrockLink.http;

import com.origin173.schoolBedrockLink.config.HttpConfig;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EmbeddedHttpServer {

    private final WebContext context;
    private HttpServer server;
    private ExecutorService executor;
    private final java.util.concurrent.Semaphore remotePermits = new java.util.concurrent.Semaphore(2);

    public EmbeddedHttpServer(WebContext context) {
        this.context = context;
    }

    public synchronized void start(HttpConfig config) throws IOException {
        if (server != null) {
            return;
        }
        InetSocketAddress address = new InetSocketAddress(config.bindAddress(), config.port());
        server = HttpServer.create(address, 32);
        registerContexts(config, config.publicBasePath());
        executor = new java.util.concurrent.ThreadPoolExecutor(4, 4, 0L,
                java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(32),
                runnable -> {
            Thread thread = new Thread(runnable, "SchoolBedrockLink-http");
            thread.setDaemon(true);
            return thread;
        }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor);
        server.start();
    }

    private void registerContexts(HttpConfig config, String prefix) {
        server.createContext(prefix + "/oauth/callback", new RemoteAdmissionHandler(remotePermits, new OAuthCallbackHandler(context)));
        server.createContext(prefix + "/start", new StartHandler(context));
        server.createContext(prefix + "/confirm", new RemoteAdmissionHandler(remotePermits, new ConfirmHandler(context)));
        server.createContext(prefix + "/healthz", new HealthHandler(context));
        if (prefix.isBlank()) {
            server.createContext("/", new RootHandler(context));
        } else {
            server.createContext(prefix, new RootHandler(context));
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public synchronized boolean isRunning() {
        return server != null;
    }
}
