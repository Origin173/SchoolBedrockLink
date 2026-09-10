package com.origin173.schoolBedrockLink.http;

import com.origin173.schoolBedrockLink.floodgate.BedrockIdentity;
import com.origin173.schoolBedrockLink.oauth.ConfirmationSession;
import com.origin173.schoolBedrockLink.oauth.MinecraftProfile;
import com.origin173.schoolBedrockLink.security.HtmlEscaper;
import java.util.List;

public final class HtmlPages {

    private HtmlPages() {
    }

    public static String home() {
        return home("/start");
    }

    public static String home(String startAction) {
        return page("校园 Minecraft 账号绑定", ""
                + "<h1>校园 Minecraft Bedrock 账号绑定</h1>"
                + "<p>请输入游戏内显示的认证码，然后使用学校皮肤站完成认证。</p>"
                + "<form action=\"" + HtmlEscaper.escape(startAction) + "\" method=\"get\">"
                + "<label for=\"code\">认证码</label>"
                + "<input id=\"code\" name=\"code\" maxlength=\"32\" minlength=\"8\" "
                + "pattern=\"[0-9A-HJKMNP-TV-Za-hjkmnp-tv-z]{8,32}\" autocomplete=\"off\" required autofocus>"
                + "<button type=\"submit\">开始认证</button>"
                + "</form>");
    }

    public static String selection(ConfirmationSession session) {
        return selection(session, "/confirm");
    }

    public static String selection(ConfirmationSession session, String confirmAction) {
        BedrockIdentity identity = session.pending().identity();
        List<MinecraftProfile> profiles = session.profiles();
        StringBuilder body = new StringBuilder();
        body.append("<h1>确认校园 Minecraft 账号</h1>");
        body.append("<p>Xbox：<strong>").append(HtmlEscaper.escape(identity.gamertag())).append("</strong></p>");
        body.append("<p>请选择用于 Bedrock 的 Minecraft 角色：</p>");
        body.append("<form action=\"").append(HtmlEscaper.escape(confirmAction)).append("\" method=\"post\">");
        body.append("<input type=\"hidden\" name=\"confirmationToken\" value=\"")
                .append(HtmlEscaper.escape(session.token())).append("\">");
        for (int index = 0; index < profiles.size(); index++) {
            MinecraftProfile profile = profiles.get(index);
            body.append("<label class=\"option\"><input type=\"radio\" name=\"profileIndex\" value=\"")
                    .append(index).append("\"")
                    .append(index == 0 ? " checked" : "")
                    .append("> ")
                    .append(HtmlEscaper.escape(profile.username()))
                    .append("</label>");
        }
        body.append("<p class=\"notice\">以后 Java 和 Bedrock 会使用同一份服务器玩家数据。</p>");
        body.append("<button type=\"submit\">确认绑定</button></form>");
        return page("确认绑定", body.toString());
    }

    public static String success(BedrockIdentity identity, MinecraftProfile profile) {
        return page("绑定成功", "<h1>绑定成功！</h1>"
                + "<p>Xbox：<strong>" + HtmlEscaper.escape(identity.gamertag()) + "</strong></p>"
                + "<p>学校 Minecraft：<strong>" + HtmlEscaper.escape(profile.username()) + "</strong></p>"
                + "<p>请关闭本页面并重新连接 Minecraft 服务器。</p>");
    }

    public static String error(String title, String message) {
        return page(title, "<h1>" + HtmlEscaper.escape(title) + "</h1><p>"
                + HtmlEscaper.escape(message).replace("\n", "<br>") + "</p>");
    }

    public static String error(String title, String message, String requestId) {
        return page(title, "<h1>" + HtmlEscaper.escape(title) + "</h1><p>"
                + HtmlEscaper.escape(message).replace("\n", "<br>")
                + "</p><small>错误编号：" + HtmlEscaper.escape(requestId) + "</small>");
    }

    private static String page(String title, String body) {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + HtmlEscaper.escape(title) + "</title><style>"
                + "*{box-sizing:border-box}body{margin:0;background:#f4f6f8;color:#17202a;"
                + "font-family:system-ui,-apple-system,Segoe UI,sans-serif;line-height:1.6}"
                + ".card{width:min(92vw,34rem);margin:8vh auto;padding:2rem;background:#fff;"
                + "border:1px solid #dfe5eb;border-radius:1rem;box-shadow:0 12px 32px #17202a12}"
                + "h1{font-size:1.45rem;line-height:1.3;margin:0 0 1rem}p{margin:0 0 1rem}"
                + "label{display:block;font-weight:600;margin:.8rem 0 .35rem}"
                + "input:not([type=radio]){width:100%;padding:.8rem;border:1px solid #aeb8c2;"
                + "border-radius:.55rem;font-size:1rem;letter-spacing:.08em;text-transform:uppercase}"
                + ".option{font-weight:400;padding:.65rem .8rem;border:1px solid #dfe5eb;border-radius:.55rem}"
                + ".option input{margin-right:.5rem}button{width:100%;padding:.8rem 1rem;margin-top:1rem;"
                + "border:0;border-radius:.55rem;background:#1769aa;color:#fff;font-size:1rem;font-weight:700;cursor:pointer}"
                + ".notice{margin-top:1rem;padding:.75rem;background:#eef6ff;border-radius:.55rem;font-size:.95rem}"
                + "small{color:#52606d}</style></head><body><main class=\"card\">" + body
                + "</main></body></html>";
    }
}
