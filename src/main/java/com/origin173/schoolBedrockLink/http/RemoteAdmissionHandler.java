package com.origin173.schoolBedrockLink.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/** Reserves HTTP worker capacity for the home/start/health routes during slow upstream calls. */
public final class RemoteAdmissionHandler implements HttpHandler {
    private final Semaphore permits;
    private final HttpHandler delegate;
    public RemoteAdmissionHandler(Semaphore permits, HttpHandler delegate) {
        this.permits = permits;
        this.delegate = delegate;
    }
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!permits.tryAcquire()) {
            try {
                exchange.getResponseHeaders().set("Retry-After", "2");
                HttpUtil.html(exchange, 503, HtmlPages.error("认证服务繁忙", "请稍后重试当前页面。"));
            } finally { exchange.close(); }
            return;
        }
        try { delegate.handle(exchange); }
        finally { permits.release(); }
    }
}
