package com.origin173.schoolBedrockLink.config;

import java.net.URI;

public record HttpConfig(String bindAddress, int port, String publicBaseUrl, int requestTimeoutSeconds) {

    public String callbackUrl() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }
        return publicBaseUrl.replaceAll("/+$", "") + "/oauth/callback";
    }

    public String publicBasePath() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return "";
        }
        String path = URI.create(publicBaseUrl).getPath();
        if (path == null || "/".equals(path)) {
            return "";
        }
        return path.replaceAll("/+$", "");
    }

    /**
     * The complete public URL that starts OAuth for a binding code, so a player who can tap or
     * copy one link never has to retype the code on the page.
     */
    public String startUrl(String code) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank() || code == null || code.isBlank()) {
            return publicBaseUrl == null ? "" : publicBaseUrl;
        }
        return publicBaseUrl.replaceAll("/+$", "") + "/start?code=" + code;
    }

    public String route(String endpoint) {
        String normalizedEndpoint = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return publicBasePath() + normalizedEndpoint;
    }

    public boolean isPublicBasePath(String requestPath) {
        String basePath = publicBasePath();
        return "/".equals(requestPath)
                || (!basePath.isBlank() && (basePath.equals(requestPath) || (basePath + "/").equals(requestPath)));
    }
}
