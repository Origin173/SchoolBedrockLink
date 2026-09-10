package com.origin173.schoolBedrockLink.util;

import java.util.Locale;
import java.util.UUID;

/** UUID parsing used for values received from a trusted profile API. */
public final class UuidUtil {

    private UuidUtil() {
    }

    public static UUID parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("UUID is missing");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() == 32) {
            if (!normalized.matches("[0-9a-f]{32}")) {
                throw new IllegalArgumentException("UUID contains non-hex characters");
            }
            normalized = normalized.substring(0, 8) + "-"
                    + normalized.substring(8, 12) + "-"
                    + normalized.substring(12, 16) + "-"
                    + normalized.substring(16, 20) + "-"
                    + normalized.substring(20);
        } else if (!normalized.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Invalid UUID format");
        }

        return UUID.fromString(normalized);
    }

    public static String canonical(UUID uuid) {
        return uuid.toString().toLowerCase(Locale.ROOT);
    }
}
