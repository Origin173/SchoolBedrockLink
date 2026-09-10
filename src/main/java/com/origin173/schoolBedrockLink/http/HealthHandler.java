package com.origin173.schoolBedrockLink.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

public final class HealthHandler implements HttpHandler {

    private final WebContext context;

    public HealthHandler(WebContext context) {
        this.context = context;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtil.methodNotAllowed(exchange, "GET");
                return;
            }
            boolean floodgate = context.floodgate().isApiAvailable();
            boolean playerLink = context.floodgate().isReady();
            boolean oauth = context.config().oauthConfigured();
            boolean http = context.httpRunning();
            boolean ready = floodgate && playerLink && oauth && http && context.registry().isHealthy();
            String json = "{\"status\":\"" + (ready ? "ok" : "not_ready") + "\","
                    + "\"floodgate\":" + floodgate + ","
                    + "\"playerLink\":" + playerLink + ","
                    + "\"oauthConfigured\":" + oauth + ","
                    + "\"http\":" + http + "}";
            HttpUtil.json(exchange, ready ? 200 : 503, json);
        } finally {
            exchange.close();
        }
    }
}
