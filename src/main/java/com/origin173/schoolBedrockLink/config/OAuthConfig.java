package com.origin173.schoolBedrockLink.config;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** OAuth settings. The secret is deliberately excluded from toString(). */
public final class OAuthConfig {

    private final String mode;
    private final URI authorizationUrl;
    private final URI tokenUrl;
    private final URI userEndpoint;
    private final URI playersEndpoint;
    private final URI yggdrasilProfilesEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final List<String> scopes;
    private final boolean pkce;

    public OAuthConfig(String mode, URI authorizationUrl, URI tokenUrl, URI userEndpoint,
                       URI playersEndpoint, URI yggdrasilProfilesEndpoint,
                       String clientId, String clientSecret, List<String> scopes, boolean pkce) {
        this.mode = mode;
        this.authorizationUrl = authorizationUrl;
        this.tokenUrl = tokenUrl;
        this.userEndpoint = userEndpoint;
        this.playersEndpoint = playersEndpoint;
        this.yggdrasilProfilesEndpoint = yggdrasilProfilesEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scopes = List.copyOf(scopes);
        this.pkce = pkce;
    }

    public String mode() {
        return mode;
    }

    public URI authorizationUrl() {
        return authorizationUrl;
    }

    public URI tokenUrl() {
        return tokenUrl;
    }

    public URI userEndpoint() {
        return userEndpoint;
    }

    public URI playersEndpoint() {
        return playersEndpoint;
    }

    public URI yggdrasilProfilesEndpoint() {
        return yggdrasilProfilesEndpoint;
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public List<String> scopes() {
        return scopes;
    }

    public boolean pkce() {
        return pkce;
    }

    public boolean configured(String callbackUrl) {
        return "blessing-skin".equalsIgnoreCase(mode)
                && isHttpUrl(authorizationUrl)
                && isHttpUrl(tokenUrl)
                && isHttpUrl(userEndpoint)
                && isHttpUrl(playersEndpoint)
                && isHttpUrl(yggdrasilProfilesEndpoint)
                && clientId != null && !clientId.isBlank()
                && !"CHANGE_ME".equalsIgnoreCase(clientId.trim())
                && clientSecret != null && !clientSecret.isBlank()
                && callbackUrl != null && !callbackUrl.isBlank()
                && pkce;
    }

    public boolean equivalentTo(OAuthConfig other) {
        return other != null
                && Objects.equals(mode, other.mode)
                && Objects.equals(authorizationUrl, other.authorizationUrl)
                && Objects.equals(tokenUrl, other.tokenUrl)
                && Objects.equals(userEndpoint, other.userEndpoint)
                && Objects.equals(playersEndpoint, other.playersEndpoint)
                && Objects.equals(yggdrasilProfilesEndpoint, other.yggdrasilProfilesEndpoint)
                && Objects.equals(clientId, other.clientId)
                && Objects.equals(clientSecret, other.clientSecret)
                && Objects.equals(scopes, other.scopes)
                && pkce == other.pkce;
    }

    private static boolean isHttpUrl(URI uri) {
        return uri != null && uri.isAbsolute()
                && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null;
    }

    @Override
    public String toString() {
        return "OAuthConfig{mode='" + mode + "', authorizationUrl=" + authorizationUrl
                + ", tokenUrl=" + tokenUrl + ", userEndpoint=" + userEndpoint
                + ", playersEndpoint=" + playersEndpoint
                + ", yggdrasilProfilesEndpoint=" + yggdrasilProfilesEndpoint
                + ", clientId='" + clientId + "', scopes=" + scopes + ", pkce=" + pkce + "}";
    }
}
