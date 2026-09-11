package com.origin173.schoolBedrockLink;

import com.origin173.schoolBedrockLink.config.HttpConfig;
import com.origin173.schoolBedrockLink.config.MessageConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnlinkedMessageTest {

    @Test
    void unlinkedMessageCarriesOneTapLinkAndKeepsTheManualFallback() {
        HttpConfig http = new HttpConfig("127.0.0.1", 8787, "https://mc-auth.example.edu/auth", 10);
        MessageConfig messages = new MessageConfig("{link}|{url}|{code}|{minutes}", "r", "c", "n");

        assertEquals("https://mc-auth.example.edu/auth/start?code=B7K2QX9M3P"
                        + "|https://mc-auth.example.edu/auth|B7K2QX9M3P|5",
                messages.unlinked(http.publicBaseUrl(), http.startUrl("B7K2QX9M3P"), "B7K2QX9M3P", 5));
    }

    @Test
    void startUrlKeepsTheReverseProxyPrefixAndTrimsTrailingSlashes() {
        HttpConfig withSlash = new HttpConfig("127.0.0.1", 8787, "https://mc-auth.example.edu/auth/", 10);
        HttpConfig without = new HttpConfig("127.0.0.1", 8787, "https://mc-auth.example.edu/auth", 10);

        assertEquals("https://mc-auth.example.edu/auth/start?code=ABC", withSlash.startUrl("ABC"));
        assertEquals("https://mc-auth.example.edu/auth/start?code=ABC", without.startUrl("ABC"));
    }

    @Test
    void unconfiguredPublicUrlStillRendersAMessage() {
        HttpConfig blank = new HttpConfig("127.0.0.1", 8787, "", 10);
        MessageConfig messages = new MessageConfig("{link}|{url}", "r", "c", "n");

        assertEquals("|", messages.unlinked(blank.publicBaseUrl(), blank.startUrl("ABC"), null, 5));
    }
}
