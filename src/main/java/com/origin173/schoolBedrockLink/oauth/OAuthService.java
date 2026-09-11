package com.origin173.schoolBedrockLink.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.origin173.schoolBedrockLink.config.OAuthConfig;
import com.origin173.schoolBedrockLink.security.PkceUtil;
import com.origin173.schoolBedrockLink.util.LimitedHttpResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** OAuth2 Authorization Code + PKCE transport for the configured Blessing Skin instance. */
public final class OAuthService {

    private static final int MAX_RESPONSE_BYTES = 1_048_576;

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile OAuthConfig config;

    public OAuthService(OAuthConfig config, Duration requestTimeout) {
        this.config = Objects.requireNonNull(config, "config");
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public void updateConfig(OAuthConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public String authorizationUrl(OAuthAuthorizationSession session, String callbackUrl) {
        return authorizationUrl(session, callbackUrl, config);
    }

    public String authorizationUrl(OAuthAuthorizationSession session, String callbackUrl,
                                   OAuthConfig snapshot) {
        OAuthConfig current = Objects.requireNonNull(snapshot, "snapshot");
        if (!current.configured(callbackUrl)) {
            throw new OAuthException("OAuth is not configured");
        }
        List<String> parameters = new ArrayList<>();
        parameters.add(formParameter("response_type", "code"));
        parameters.add(formParameter("client_id", current.clientId()));
        parameters.add(formParameter("redirect_uri", callbackUrl));
        if (!current.scopes().isEmpty()) {
            parameters.add(formParameter("scope", String.join(" ", current.scopes())));
        }
        parameters.add(formParameter("state", session.state()));
        parameters.add(formParameter("code_challenge", PkceUtil.challengeFor(session.codeVerifier())));
        parameters.add(formParameter("code_challenge_method", "S256"));
        return appendQuery(current.authorizationUrl(), String.join("&", parameters));
    }

    public OAuthToken exchangeCode(OAuthAuthorizationSession session, String code, String callbackUrl)
            throws OAuthException {
        return exchangeCode(session, code, callbackUrl, config);
    }

    public OAuthToken exchangeCode(OAuthAuthorizationSession session, String code, String callbackUrl,
                                   OAuthConfig snapshot) throws OAuthException {
        if (code == null || code.isBlank() || code.length() > 2048) {
            throw new OAuthException("authorization code is invalid");
        }
        OAuthConfig current = Objects.requireNonNull(snapshot, "snapshot");
        if (!current.configured(callbackUrl)) {
            throw new OAuthException("OAuth is not configured");
        }
        StringBuilder form = new StringBuilder();
        appendForm(form, "grant_type", "authorization_code");
        appendForm(form, "code", code);
        appendForm(form, "redirect_uri", callbackUrl);
        appendForm(form, "client_id", current.clientId());
        appendForm(form, "code_verifier", session.codeVerifier());
        if (current.clientSecret() != null && !current.clientSecret().isBlank()) {
            appendForm(form, "client_secret", current.clientSecret());
        }

        HttpRequest request = HttpRequest.newBuilder(current.tokenUrl())
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
                .build();
        LimitedHttpResponse.Response response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new OAuthException("token endpoint rejected the request", response.statusCode());
        }

        try {
            JsonNode root = mapper.readTree(body(response.body()));
            JsonNode accessToken = root == null ? null : root.get("access_token");
            if (accessToken == null || !accessToken.isTextual() || accessToken.textValue().isBlank()) {
                throw new OAuthException("token response has no access token");
            }
            // refresh_token, if returned, is intentionally ignored and never stored.
            return new OAuthToken(accessToken.textValue());
        } catch (IOException exception) {
            throw new OAuthException("token response is not valid JSON", exception);
        }
    }

    private LimitedHttpResponse.Response send(HttpRequest request) throws OAuthException {
        try {
            return LimitedHttpResponse.send(httpClient, request, MAX_RESPONSE_BYTES);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OAuthException("OAuth request interrupted", exception);
        } catch (IOException exception) {
            throw new OAuthException("OAuth request failed", exception);
        }
    }

    private static String body(byte[] bytes) throws OAuthException {
        if (bytes == null || bytes.length == 0) {
            throw new OAuthException("OAuth response is empty");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String appendQuery(URI uri, String query) {
        String raw = uri.toString();
        String separator = raw.contains("?") ? (raw.endsWith("?") || raw.endsWith("&") ? "" : "&") : "?";
        return raw + separator + query;
    }

    private static String formParameter(String key, String value) {
        return encode(key) + "=" + encode(value);
    }

    private static void appendForm(StringBuilder form, String key, String value) {
        if (form.length() > 0) {
            form.append('&');
        }
        form.append(formParameter(key, value));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public static final class OAuthException extends RuntimeException {
        private int upstreamStatus;
        public int upstreamStatus() { return upstreamStatus; }
        public OAuthException(String message, int upstreamStatus) {
            super(message);
            this.upstreamStatus = upstreamStatus;
        }
        public OAuthException(String message) {
            super(message);
        }

        public OAuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
