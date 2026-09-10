package com.origin173.schoolBedrockLink.security;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates all browser and in-game tokens from a cryptographically secure source. */
public final class SecureTokenGenerator {

    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private final SecureRandom random;

    public SecureTokenGenerator() {
        this(new SecureRandom());
    }

    public SecureTokenGenerator(SecureRandom random) {
        this.random = random;
    }

    public String crockfordBase32(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("Token length must be positive");
        }
        char[] result = new char[length];
        for (int index = 0; index < length; index++) {
            result[index] = CROCKFORD[random.nextInt(CROCKFORD.length)];
        }
        return new String(result);
    }

    public String base64Url(int byteLength) {
        if (byteLength < 1) {
            throw new IllegalArgumentException("Token byte length must be positive");
        }
        byte[] bytes = new byte[byteLength];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
