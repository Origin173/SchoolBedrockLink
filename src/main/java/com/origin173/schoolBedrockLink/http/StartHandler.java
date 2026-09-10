package com.origin173.schoolBedrockLink.http;

import com.origin173.schoolBedrockLink.config.PluginConfig;
import com.origin173.schoolBedrockLink.link.PendingLink;
import com.origin173.schoolBedrockLink.oauth.OAuthAuthorizationSession;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

public final class StartHandler implements HttpHandler {

    private final WebContext context;

    public StartHandler(WebContext context) {
        this.context = context;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String stage = "method";
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtil.methodNotAllowed(exchange, "GET");
                return;
            }
            if (!context.allowRequest(exchange)) {
                HttpUtil.tooManyRequests(exchange);
                return;
            }
            Map<String, String> params = HttpUtil.query(exchange);
            stage = "validate-code";
            String code = params.get("code");
            Instant now = Instant.now();
            String normalizedCode = com.origin173.schoolBedrockLink.link.PendingLinkService.normalize(code);
            if (normalizedCode == null) {
                HttpUtil.html(exchange, 400, HtmlPages.error("认证码无效", "认证码无效或已过期，请重新进入服务器获取新的认证码。"));
                return;
            }
            if (!context.allowOAuthStart(normalizedCode)) {
                HttpUtil.tooManyRequests(exchange, "同一认证码的认证请求过于频繁，请稍后再试。");
                return;
            }
            stage = "find-pending-code";
            PendingLink pending = context.pending().find(code, now).orElse(null);
            if (pending == null) {
                HttpUtil.html(exchange, 400, HtmlPages.error("认证码无效", "认证码无效或已过期，请重新进入服务器获取新的认证码。"));
                return;
            }
            PluginConfig config = context.config();
            stage = "validate-oauth-config";
            if (!config.oauthConfigured()) {
                HttpUtil.serviceUnavailable(exchange);
                return;
            }
            stage = "create-oauth-redirect";
            OAuthAuthorizationSession session = context.sessions().createAuthorization(pending, now,
                    config.bindingLifetime(), config);
            String location = context.oauth().authorizationUrl(session, config.http().callbackUrl(), config.oauth());
            HttpUtil.redirect(exchange, location, config.oauth().authorizationUrl());
        } catch (HttpUtil.BadRequestException exception) {
            HttpUtil.badRequest(exchange);
        } catch (RuntimeException exception) {
            String requestId = HttpErrorReporter.report(context.logger(), stage, "start-unexpected", 503, exception);
            HttpUtil.html(exchange, 503,
                    HtmlPages.error("认证服务暂不可用", "请稍后重试；如持续失败，请联系管理员。", requestId));
        } finally {
            exchange.close();
        }
    }
}
