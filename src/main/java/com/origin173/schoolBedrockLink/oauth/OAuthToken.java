package com.origin173.schoolBedrockLink.oauth;

/** Short-lived in-memory access token. Do not add persistence or a verbose toString. */
public final class OAuthToken {

    private final String accessToken;

    public OAuthToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("access token is missing");
        }
        this.accessToken = accessToken;
    }

    public String accessToken() {
        return accessToken;
    }

    @Override
    public String toString() {
        return "OAuthToken{present=true}";
    }
}
