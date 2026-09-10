package com.origin173.schoolBedrockLink.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

public final class RootHandler implements HttpHandler {

    private final WebContext context;

    public RootHandler(WebContext context) {
        this.context = context;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtil.methodNotAllowed(exchange, "GET");
                return;
            }
            if (!context.config().http().isPublicBasePath(exchange.getRequestURI().getPath())) {
                HttpUtil.html(exchange, 404, HtmlPages.error("页面不存在", "请检查认证地址。"));
                return;
            }
            if (!context.allowRequest(exchange)) {
                HttpUtil.tooManyRequests(exchange);
                return;
            }
            var config = context.config();
            HttpUtil.html(exchange, 200, HtmlPages.home(config.http().route("/start")), config.oauth().authorizationUrl());
        } catch (RuntimeException exception) {
            HttpUtil.html(exchange, 500, HtmlPages.error("服务错误", "请稍后重试。"));
        } finally {
            exchange.close();
        }
    }
}
