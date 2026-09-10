package com.origin173.schoolBedrockLink.http;

import java.util.UUID;
import java.util.logging.Logger;

/** Emits safe, correlated diagnostics without recording request credentials or URLs. */
public final class HttpErrorReporter {

    private HttpErrorReporter() {
    }

    public static String report(Logger logger, String stage, String category, int status, Throwable failure) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String exceptionType = failure == null ? "none" : failure.getClass().getSimpleName();
        int upstreamStatus = failure instanceof com.origin173.schoolBedrockLink.oauth.OAuthService.OAuthException oauth
                ? oauth.upstreamStatus()
                : failure instanceof com.origin173.schoolBedrockLink.oauth.BlessingSkinProfileService.ProfileException profile
                ? profile.upstreamStatus() : 0;
        String causeType = failure == null || failure.getCause() == null
                ? "none" : failure.getCause().getClass().getSimpleName();
        logger.warning("HTTP failure requestId=" + requestId
                + " stage=" + stage
                + " category=" + category
                + " status=" + status
                + " upstreamStatus=" + upstreamStatus
                + " exception=" + exceptionType + " cause=" + causeType
                + profileDetail(failure));
        return requestId;
    }

    /**
     * Only {@code ProfileException} messages are logged. They are built from fixed
     * text plus JSON structural metadata (field names, element counts), so they never
     * carry credentials or player names. Other exception types keep their messages out
     * of the log, and credential-looking substrings are redacted as defence in depth.
     */
    private static String profileDetail(Throwable failure) {
        if (!(failure instanceof com.origin173.schoolBedrockLink.oauth.BlessingSkinProfileService.ProfileException)) {
            return "";
        }
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return "";
        }
        String sanitized = message
                .replaceAll("(?i)([?&](?:access_token|token|code|client_secret|secret|state)=)[^&\\s]*",
                        "$1<redacted>")
                .replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (sanitized.length() > 240) {
            sanitized = sanitized.substring(0, 240) + "...";
        }
        return " detail=" + sanitized;
    }
}
