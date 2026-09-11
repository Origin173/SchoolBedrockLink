package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.security.RateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void nullOrBlankKeyIsRejectedInsteadOfThrowing() {
        RateLimiter limiter = new RateLimiter(5, Duration.ofMinutes(1));

        // WebContext.allowOAuthStart relies on this and no longer guards the key itself, so a null
        // code must stay a rejection rather than an exception.
        assertFalse(limiter.tryAcquire(null));
        assertFalse(limiter.tryAcquire("   "));
        assertTrue(limiter.tryAcquire("B7K2QX9M3P"));
    }

    @Test
    void rejectsOnceTheWindowBudgetIsSpent() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1));

        assertTrue(limiter.tryAcquire("same-code"));
        assertTrue(limiter.tryAcquire("same-code"));
        assertFalse(limiter.tryAcquire("same-code"));
        assertTrue(limiter.tryAcquire("other-code"));
    }
}
