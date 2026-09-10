package com.origin173.schoolBedrockLink.config;

public record RateLimitConfig(boolean enabled, int perIpPerMinute, int oauthStartPerCode) {
}
