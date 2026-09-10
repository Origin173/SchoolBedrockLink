package com.origin173.schoolBedrockLink.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import com.sun.net.httpserver.HttpExchange;

public final class HttpUtil {

    private static final int MAX_QUERY_LENGTH = 4096;

    private HttpUtil() {
    }

    public static Map<String, String> query(HttpExchange exchange) throws BadRequestException {
        String query = exchange.getRequestURI().getRawQuery();
        return parseParameters(query == null ? "" : query, MAX_QUERY_LENGTH);
    }

    public static Map<String, String> form(HttpExchange exchange, int maxBytes)
            throws IOException, BadRequestException {
        String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength) > maxBytes) {
                    throw new BadRequestException("request too large");
                }
            } catch (NumberFormatException exception) {
                throw new BadRequestException("invalid content length");
            }
        }

        try (InputStream input = exchange.getRequestBody()) {
            byte[] buffer = new byte[maxBytes + 1];
            int offset = 0;
            while (offset < buffer.length) {
                int read = input.read(buffer, offset, buffer.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            if (offset > maxBytes) {
                throw new BadRequestException("request too large");
            }
            return parseParameters(new String(buffer, 0, offset, StandardCharsets.UTF_8), maxBytes);
        }
    }

    public static Map<String, String> parseParameters(String raw, int maxLength)
            throws BadRequestException {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        if (raw.length() > maxLength) {
            throw new BadRequestException("parameters too long");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : raw.split("&", -1)) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String rawKey = separator < 0 ? pair : pair.substring(0, separator);
            String rawValue = separator < 0 ? "" : pair.substring(separator + 1);
            String key = decode(rawKey);
            String value = decode(rawValue);
            if (key.isBlank() || result.putIfAbsent(key, value) != null) {
                throw new BadRequestException("duplicate or empty parameter");
            }
        }
        return result;
    }

    public static void html(HttpExchange exchange, int status, String html) throws IOException {
        html(exchange, status, html, null);
    }

    public static void html(HttpExchange exchange, int status, String html, java.net.URI authorization) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        securityHeaders(exchange, authorization);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    public static void json(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        securityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    public static void redirect(HttpExchange exchange, String location) throws IOException {
        redirect(exchange, location, null);
    }

    public static void redirect(HttpExchange exchange, String location, java.net.URI authorization) throws IOException {
        securityHeaders(exchange, authorization);
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    public static void methodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        html(exchange, 405, HtmlPages.error("请求方法不支持", "请使用页面上的按钮继续。"));
    }

    public static void tooManyRequests(HttpExchange exchange) throws IOException {
        html(exchange, 429, HtmlPages.error("请求过于频繁", "请稍后再试。"));
    }

    public static void tooManyRequests(HttpExchange exchange, String message) throws IOException {
        html(exchange, 429, HtmlPages.error("请求过于频繁", message));
    }

    public static void badRequest(HttpExchange exchange) throws IOException {
        html(exchange, 400, HtmlPages.error("请求无效", "页面参数无效或已损坏，请重新开始认证。"));
    }

    public static void serviceUnavailable(HttpExchange exchange) throws IOException {
        html(exchange, 503, HtmlPages.error("服务暂不可用", "认证服务尚未配置完成，请联系服务器管理员。"));
    }

    private static String decode(String value) throws BadRequestException {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("invalid URL encoding");
        }
    }

    private static void securityHeaders(HttpExchange exchange) {
        securityHeaders(exchange, null);
    }

    private static void securityHeaders(HttpExchange exchange, java.net.URI authorization) {
        var headers = exchange.getResponseHeaders();
        headers.set("Cache-Control", "no-store");
        headers.set("Pragma", "no-cache");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'" + authorizationOrigin(authorization) + "; "
                        + "frame-ancestors 'none'; base-uri 'none'");
    }

    private static String authorizationOrigin(java.net.URI uri) {
        if (uri == null) return "";
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Invalid authorization origin");
        }
        return " " + uri.getScheme().toLowerCase(java.util.Locale.ROOT) + "://" + uri.getHost()
                + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }

    public static final class BadRequestException extends Exception {
        public BadRequestException(String message) {
            super(message);
        }
    }
}
