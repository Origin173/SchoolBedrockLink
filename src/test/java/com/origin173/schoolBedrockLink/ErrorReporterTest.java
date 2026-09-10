package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.http.HttpErrorReporter;
import com.origin173.schoolBedrockLink.oauth.BlessingSkinProfileService;
import com.origin173.schoolBedrockLink.oauth.OAuthService;
import java.util.logging.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ErrorReporterTest {
    @Test
    void safeDiagnosticsIncludeStageAndStatusesWithoutExceptionMessageOrCredentials() {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var records = new StringBuilder();
        logger.addHandler(new Handler() {
            public void publish(LogRecord record) { records.append(record.getMessage()); }
            public void flush() { }
            public void close() { }
        });
        String id = HttpErrorReporter.report(logger, "exchange-token", "oauth-provider", 502,
                new OAuthService.OAuthException("secret=DO_NOT_LOG&state=DO_NOT_LOG&code=DO_NOT_LOG", 401));
        assertTrue(records.toString().contains(id));
        assertTrue(records.toString().contains("stage=exchange-token"));
        assertTrue(records.toString().contains("status=502"));
        assertTrue(records.toString().contains("upstreamStatus=401"));
        assertFalse(records.toString().contains("DO_NOT_LOG"));
    }

    @Test
    void profileShapeDiagnosticsAreLoggedForPolicyFailures() {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var records = new StringBuilder();
        logger.addHandler(new Handler() {
            public void publish(LogRecord record) { records.append(record.getMessage()); }
            public void flush() { }
            public void close() { }
        });
        HttpErrorReporter.report(logger, "fetch-profiles", "profile-players_response_invalid", 502,
                new BlessingSkinProfileService.ProfileException(
                        BlessingSkinProfileService.Kind.PLAYERS_RESPONSE_INVALID,
                        "Blessing Skin /api/players returned object(fields=code,message)"));
        assertTrue(records.toString().contains("detail="), "shape diagnostic must be logged");
        assertTrue(records.toString().contains("object(fields=code,message)"));
    }

    @Test
    void credentialShapedTextInsideProfileDiagnosticsIsRedacted() {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var records = new StringBuilder();
        logger.addHandler(new Handler() {
            public void publish(LogRecord record) { records.append(record.getMessage()); }
            public void flush() { }
            public void close() { }
        });
        HttpErrorReporter.report(logger, "fetch-profiles", "profile-network", 502,
                new BlessingSkinProfileService.ProfileException(
                        BlessingSkinProfileService.Kind.NETWORK,
                        "upstream ?access_token=DO_NOT_LOG&code=DO_NOT_LOG"));
        assertFalse(records.toString().contains("DO_NOT_LOG"));
    }
}
