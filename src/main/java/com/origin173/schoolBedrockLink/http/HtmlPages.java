package com.origin173.schoolBedrockLink.http;

import com.origin173.schoolBedrockLink.floodgate.BedrockIdentity;
import com.origin173.schoolBedrockLink.oauth.ConfirmationSession;
import com.origin173.schoolBedrockLink.oauth.MinecraftProfile;
import com.origin173.schoolBedrockLink.security.HtmlEscaper;
import java.util.List;

/**
 * Renders every browser-facing page.
 *
 * <p>Styling is inlined because the response CSP is {@code default-src 'none'; style-src
 * 'unsafe-inline'} — external stylesheets, web fonts and images cannot be loaded, so the sheet
 * below must stay self-contained and font stacks must resolve to locally installed faces.
 */
public final class HtmlPages {

    private static final String LINK_MARK = """
            <svg class="mark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" \
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">\
            <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>\
            <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>""";

    private static final String CHECK_MARK = """
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" \
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">\
            <circle cx="12" cy="12" r="9.2"/><path d="m8.2 12.4 2.6 2.6 5-5.6"/></svg>""";

    private static final String ALERT_MARK = """
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" \
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">\
            <circle cx="12" cy="12" r="9.2"/><path d="M12 7.6v5.2"/><path d="M12 16.4h.01"/></svg>""";

    private static final String STYLES = """
            *,*::before,*::after{box-sizing:border-box}
            :root{
            color-scheme:light;
            --canvas:#fafafa;
            --elevated:#ffffff;
            --ink:#171717;
            --body:#4d4d4d;
            --hairline:#ebebeb;
            --hairline-strong:#dadada;
            --link:#0070f3;
            --link-soft:#d3e5ff;
            --error:#ee0000;
            --shadow:0 1px 1px rgba(0,0,0,.04),0 8px 16px -4px rgba(0,0,0,.06);
            --sans:Geist,Inter,system-ui,-apple-system,'Segoe UI','Microsoft YaHei','PingFang SC','Hiragino Sans GB','Noto Sans SC',sans-serif;
            --mono:'Geist Mono',ui-monospace,SFMono-Regular,Menlo,Consolas,'JetBrains Mono',monospace;
            }
            html{-webkit-text-size-adjust:100%}
            body{
            margin:0;min-height:100vh;min-height:100dvh;
            display:flex;flex-direction:column;
            padding:clamp(24px,6vh,64px) 20px;
            background:var(--canvas);color:var(--body);
            font-family:var(--sans);font-size:16px;line-height:24px;
            -webkit-font-smoothing:antialiased;-moz-osx-font-smoothing:grayscale;
            }
            .mesh{
            position:fixed;top:0;left:0;right:0;height:min(56vh,440px);
            z-index:0;pointer-events:none;
            background:
            radial-gradient(40% 52% at 12% 26%,rgba(0,124,240,.22),transparent 66%),
            radial-gradient(34% 46% at 34% 4%,rgba(0,223,216,.22),transparent 68%),
            radial-gradient(36% 48% at 82% 16%,rgba(121,40,202,.2),transparent 68%),
            radial-gradient(30% 42% at 66% 32%,rgba(255,0,128,.14),transparent 70%),
            radial-gradient(28% 38% at 96% 42%,rgba(249,203,40,.18),transparent 72%);
            filter:blur(16px);
            -webkit-mask-image:linear-gradient(to bottom,#000 0%,rgba(0,0,0,.5) 52%,transparent 100%);
            mask-image:linear-gradient(to bottom,#000 0%,rgba(0,0,0,.5) 52%,transparent 100%);
            }
            .shell{position:relative;z-index:1;margin:auto;width:100%;max-width:32rem;display:flex;flex-direction:column;gap:20px}
            .masthead{display:flex;align-items:center;justify-content:center;gap:9px;color:var(--ink)}
            .mark{width:22px;height:22px;flex:none;display:block}
            .wordmark{font-family:var(--mono);font-size:13px;line-height:20px;font-weight:500;color:var(--ink)}
            .card{background:var(--elevated);border:1px solid var(--hairline);border-radius:16px;
            padding:clamp(22px,4.6vw,32px);box-shadow:var(--shadow)}
            h1{font-size:clamp(21px,4.6vw,26px);line-height:1.3;font-weight:600;letter-spacing:-.02em;
            color:var(--ink);margin:0 0 12px;text-wrap:balance}
            p{margin:0 0 10px;color:var(--body)}
            p:last-child{margin-bottom:0}
            strong{font-weight:600;color:var(--ink)}
            .icon{width:38px;height:38px;margin:0 0 18px;display:block}
            .icon svg{width:100%;height:100%;display:block}
            .icon-ok{color:var(--link)}
            .icon-err{color:var(--error)}
            form{margin-top:18px}
            label{display:block;font-size:14px;line-height:20px;font-weight:500;letter-spacing:-.01em;
            color:var(--ink);margin:0 0 8px}
            input:not([type=radio]){width:100%;padding:13px 14px;margin:0;
            font-family:var(--mono);font-size:16px;line-height:20px;letter-spacing:.14em;text-transform:uppercase;
            color:var(--ink);background:var(--elevated);
            border:1px solid var(--hairline);border-radius:6px;
            -webkit-appearance:none;appearance:none;
            transition:border-color .16s ease,box-shadow .16s ease}
            input:not([type=radio]):hover{border-color:var(--hairline-strong)}
            input:not([type=radio]):focus{outline:none;border-color:var(--link);box-shadow:0 0 0 3px var(--link-soft)}
            .option{display:flex;align-items:center;gap:10px;
            font-size:15px;font-weight:400;color:var(--ink);
            padding:13px 14px;margin:0 0 8px;
            border:1px solid var(--hairline);border-radius:6px;background:var(--elevated);cursor:pointer;
            transition:border-color .16s ease,background .16s ease,box-shadow .16s ease}
            .option:hover{background:#fcfcfc;border-color:var(--hairline-strong)}
            .option input{flex:none;width:16px;height:16px;margin:0;accent-color:var(--ink)}
            .option input:focus-visible{outline:2px solid var(--link);outline-offset:2px}
            .option:has(input:checked){border-color:var(--ink);box-shadow:inset 0 0 0 1px var(--ink)}
            button{display:flex;align-items:center;justify-content:center;
            width:100%;height:48px;margin:20px 0 0;padding:0 20px;
            font-family:var(--sans);font-size:16px;line-height:20px;font-weight:500;
            color:#fff;background:var(--ink);border:0;border-radius:100px;cursor:pointer;
            -webkit-appearance:none;appearance:none;
            transition:background .16s ease,transform .16s ease}
            button:hover{background:#000}
            button:active{transform:translateY(1px)}
            button:focus-visible{outline:2px solid var(--link);outline-offset:2px}
            .notice{margin:18px 0 0;padding:12px 16px;
            background:var(--canvas);border:1px solid var(--hairline);border-radius:12px;
            font-size:14px;line-height:22px;color:var(--body)}
            small{display:block;margin-top:18px;font-size:12px;line-height:18px;color:var(--body)}
            code{font-family:var(--mono);font-size:12px;color:var(--ink)}
            @media (max-width:420px){
            body{padding:20px 14px}
            .card{border-radius:14px}
            }
            """;

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
        return page("绑定成功", "<div class=\"icon icon-ok\">" + CHECK_MARK + "</div>"
                + "<h1>绑定成功！</h1>"
                + "<p>Xbox：<strong>" + HtmlEscaper.escape(identity.gamertag()) + "</strong></p>"
                + "<p>学校 Minecraft：<strong>" + HtmlEscaper.escape(profile.username()) + "</strong></p>"
                + "<p>请关闭本页面并重新连接 Minecraft 服务器。</p>");
    }

    public static String error(String title, String message) {
        return page(title, "<div class=\"icon icon-err\">" + ALERT_MARK + "</div>"
                + "<h1>" + HtmlEscaper.escape(title) + "</h1><p>"
                + HtmlEscaper.escape(message).replace("\n", "<br>") + "</p>");
    }

    public static String error(String title, String message, String requestId) {
        return page(title, "<div class=\"icon icon-err\">" + ALERT_MARK + "</div>"
                + "<h1>" + HtmlEscaper.escape(title) + "</h1><p>"
                + HtmlEscaper.escape(message).replace("\n", "<br>")
                + "</p><small>错误编号：<code>" + HtmlEscaper.escape(requestId) + "</code></small>");
    }

    private static String page(String title, String body) {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<meta name=\"color-scheme\" content=\"light\">"
                + "<meta name=\"theme-color\" content=\"#fafafa\">"
                + "<title>" + HtmlEscaper.escape(title) + "</title><style>" + STYLES + "</style></head><body>"
                + "<div class=\"mesh\" aria-hidden=\"true\"></div>"
                + "<div class=\"shell\">"
                + "<header class=\"masthead\">" + LINK_MARK
                + "<span class=\"wordmark\">SchoolBedrockLink</span></header>"
                + "<main class=\"card\">" + body + "</main>"
                + "</div></body></html>";
    }
}
