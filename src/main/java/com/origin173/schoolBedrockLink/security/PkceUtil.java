package com.origin173.schoolBedrockLink.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class PkceUtil {

    private PkceUtil() {
    }

    public static String newVerifier(SecureTokenGenerator tokens) {
        // 32 random bytes produce a 43-character RFC 7636-compatible verifier.
        return tokens.base64Url(32);
    }

    public static String challengeFor(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }
}
