package com.origin173.schoolBedrockLink.http;

import com.origin173.schoolBedrockLink.config.PluginConfig;
import com.origin173.schoolBedrockLink.link.PendingLink;
import com.origin173.schoolBedrockLink.oauth.ConfirmationSession;
import com.origin173.schoolBedrockLink.oauth.BlessingSkinProfileService;
import com.origin173.schoolBedrockLink.oauth.MinecraftProfile;
import com.origin173.schoolBedrockLink.oauth.OAuthAuthorizationSession;
import com.origin173.schoolBedrockLink.oauth.OAuthService;
import com.origin173.schoolBedrockLink.oauth.OAuthToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class OAuthCallbackHandler implements HttpHandler {

    private final WebContext context;

    public OAuthCallbackHandler(WebContext context) {
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
            stage = "parse-callback";
            Map<String, String> params = HttpUtil.query(exchange);
            Instant now = Instant.now();
            // Consume state before looking at provider success/failure. It is one-time
            // even when the provider returns an error.
            stage = "consume-state";
            OAuthAuthorizationSession session = context.sessions()
                    .consumeState(params.get("state"), now).orElse(null);
            if (session == null) {
                HttpUtil.html(exchange, 400, HtmlPages.error("认证会话无效", "认证会话已过期或已经使用，请重新开始认证。"));
                return;
            }
            if (params.containsKey("error") || params.get("code") == null) {
                HttpUtil.html(exchange, 400, HtmlPages.error("学校皮肤站认证未完成", "认证未完成，请重新开始认证。"));
                return;
            }

            stage = "validate-pending-code";
            PendingLink currentPending = context.pending().find(session.pending().bindingCode(), now).orElse(null);
            if (currentPending == null
                    || !currentPending.identity().bedrockUuid().equals(session.pending().identity().bedrockUuid())
                    || !currentPending.identity().xuid().equals(session.pending().identity().xuid())) {
                HttpUtil.html(exchange, 400, HtmlPages.error("认证码无效", "认证码已过期，请重新进入服务器获取新的认证码。"));
                return;
            }
            PluginConfig config = session.snapshot() == null ? context.config() : session.snapshot();
            stage = "validate-oauth-config";
            if (!config.oauthConfigured()) {
                HttpUtil.serviceUnavailable(exchange);
                return;
            }

            List<MinecraftProfile> profiles;
            try {
                stage = "exchange-token";
                OAuthToken token = context.oauth().exchangeCode(session, params.get("code"),
                        config.http().callbackUrl(), config.oauth());
                stage = "fetch-profiles";
                profiles = context.blessingSkinProfiles().fetchProfiles(token, config.oauth());
            } catch (OAuthService.OAuthException exception) {
                String requestId = HttpErrorReporter.report(context.logger(), stage, "oauth-provider", 502, exception);
                HttpUtil.html(exchange, 502, HtmlPages.error("学校皮肤站认证失败",
                        "无法读取你的 Minecraft Profile，请稍后重试。", requestId));
                return;
            } catch (BlessingSkinProfileService.ProfileException exception) {
                String message = switch (exception.kind()) {
                    case USER_UNAUTHORIZED -> "皮肤站登录授权已经失效，请重新开始认证。";
                    case PLAYERS_UNAUTHORIZED -> "无法读取你的 Minecraft 角色，请重新授权或联系管理员。";
                    case YGGDRASIL_EMPTY, YGGDRASIL_RESPONSE_INVALID -> "无法从学校 Yggdrasil 获取角色 UUID。";
                    case USER_RESPONSE_INVALID, PLAYERS_RESPONSE_INVALID, NETWORK ->
                            "无法读取你的 Minecraft 角色，请稍后重试或联系管理员。";
                };
                int status = exception.kind() == BlessingSkinProfileService.Kind.USER_UNAUTHORIZED
                        || exception.kind() == BlessingSkinProfileService.Kind.PLAYERS_UNAUTHORIZED
                        ? 401 : 502;
                String requestId = HttpErrorReporter.report(context.logger(), stage,
                        "profile-" + exception.kind().name().toLowerCase(java.util.Locale.ROOT), status, exception);
                HttpUtil.html(exchange, status, HtmlPages.error("学校皮肤站接口调用失败", message, requestId));
                return;
            }
            if (profiles.isEmpty()) {
                HttpUtil.html(exchange, 400, HtmlPages.error("没有可用 Minecraft 角色",
                        "当前皮肤站账号没有可用的 Minecraft 游戏角色。请先在学校皮肤站创建角色后重试。"));
                return;
            }

            stage = "validate-pending-before-confirmation";
            Instant confirmationNow = Instant.now();
            PendingLink latestPending = context.pending()
                    .find(session.pending().bindingCode(), confirmationNow).orElse(null);
            if (latestPending == null
                    || !latestPending.identity().bedrockUuid().equals(session.pending().identity().bedrockUuid())
                    || !latestPending.identity().xuid().equals(session.pending().identity().xuid())) {
                HttpUtil.html(exchange, 400, HtmlPages.error("认证码无效", "认证码已过期，请重新进入服务器获取新的认证码。"));
                return;
            }

            stage = "create-confirmation";
            ConfirmationSession confirmation = context.sessions().createConfirmation(latestPending, profiles,
                    confirmationNow, config.bindingLifetime());
            HttpUtil.html(exchange, 200, HtmlPages.selection(confirmation, config.http().route("/confirm")));
        } catch (HttpUtil.BadRequestException exception) {
            HttpUtil.badRequest(exchange);
        } catch (RuntimeException exception) {
            String requestId = HttpErrorReporter.report(context.logger(), stage, "callback-unexpected", 500, exception);
            HttpUtil.html(exchange, 500, HtmlPages.error("认证失败", "请重新开始认证。", requestId));
        } finally {
            exchange.close();
        }
    }
}
