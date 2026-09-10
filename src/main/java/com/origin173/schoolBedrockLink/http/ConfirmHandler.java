package com.origin173.schoolBedrockLink.http;

import com.origin173.schoolBedrockLink.config.PluginConfig;
import com.origin173.schoolBedrockLink.link.PendingLink;
import com.origin173.schoolBedrockLink.link.LinkService;
import com.origin173.schoolBedrockLink.oauth.ConfirmationSession;
import com.origin173.schoolBedrockLink.oauth.MinecraftProfile;
import com.origin173.schoolBedrockLink.oauth.ProfileSelection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public final class ConfirmHandler implements HttpHandler {

    private final WebContext context;

    public ConfirmHandler(WebContext context) {
        this.context = context;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtil.methodNotAllowed(exchange, "POST");
                return;
            }
            if (!context.allowRequest(exchange)) {
                HttpUtil.tooManyRequests(exchange);
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
                HttpUtil.badRequest(exchange);
                return;
            }
            Map<String, String> params = HttpUtil.form(exchange, 16 * 1024);
            String token = params.get("confirmationToken");
            int profileIndex;
            try {
                profileIndex = Integer.parseInt(params.getOrDefault("profileIndex", "-1"));
            } catch (NumberFormatException exception) {
                HttpUtil.badRequest(exchange);
                return;
            }
            Instant now = Instant.now();
            ConfirmationSession visible = context.sessions().findConfirmation(token, now).orElse(null);
            if (visible == null || profileIndex < 0 || profileIndex >= visible.profiles().size()) {
                HttpUtil.html(exchange, 400, HtmlPages.error("确认信息无效", "认证页面已过期，请重新开始认证。"));
                return;
            }
            PendingLink currentPending = context.pending().find(visible.pending().bindingCode(), now).orElse(null);
            if (currentPending == null
                    || !currentPending.identity().bedrockUuid().equals(visible.pending().identity().bedrockUuid())
                    || !currentPending.identity().xuid().equals(visible.pending().identity().xuid())) {
                HttpUtil.html(exchange, 400, HtmlPages.error("认证码无效", "认证码已过期，请重新进入服务器获取新的认证码。"));
                return;
            }

            // Consume only after the index has been validated. Two concurrent valid
            // submissions still leave exactly one winner.
            Optional<ConfirmationSession> consumed = context.sessions().consumeConfirmation(token, now);
            if (consumed.isEmpty()) {
                HttpUtil.html(exchange, 400, HtmlPages.error("确认信息无效", "认证页面已过期，请重新开始认证。"));
                return;
            }
            ConfirmationSession session = consumed.get();
            MinecraftProfile profile = ProfileSelection.select(session, profileIndex);
            LinkService.BindOutcome outcome = context.links().bind(currentPending.identity(), profile);
            PluginConfig config = context.config();
            switch (outcome.status()) {
                case SUCCESS, IDEMPOTENT -> {
                    context.pending().remove(currentPending.bindingCode());
                    HttpUtil.html(exchange, 200, HtmlPages.success(currentPending.identity(), profile));
                }
                case CONFLICT -> HttpUtil.html(exchange, 409,
                        HtmlPages.error("绑定冲突", "账号绑定发生冲突，请联系服务器管理员。"));
                case NOT_READY, FAILED -> HttpUtil.html(exchange, 503,
                        HtmlPages.error("绑定暂未完成", config.messages().notReady()));
            }
        } catch (HttpUtil.BadRequestException exception) {
            HttpUtil.badRequest(exchange);
        } catch (RuntimeException exception) {
            HttpUtil.html(exchange, 500, HtmlPages.error("绑定失败", "请重新开始认证。"));
        } finally {
            exchange.close();
        }
    }
}
