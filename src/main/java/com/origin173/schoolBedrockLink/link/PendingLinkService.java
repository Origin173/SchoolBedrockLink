package com.origin173.schoolBedrockLink.link;

import com.origin173.schoolBedrockLink.floodgate.BedrockIdentity;
import com.origin173.schoolBedrockLink.security.SecureTokenGenerator;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PendingLinkService {

    private static final String CODE_PATTERN = "[0-9A-HJKMNP-TV-Z]+";

    private final SecureTokenGenerator tokens;
    private final ConcurrentHashMap<String, PendingLink> pending = new ConcurrentHashMap<>();

    public PendingLinkService(SecureTokenGenerator tokens) {
        this.tokens = tokens;
    }

    public synchronized PendingLink getOrCreate(BedrockIdentity identity, int codeLength, int expireSeconds,
                                                boolean reuseExisting, Instant now) {
        cleanup(now);
        if (reuseExisting) {
            for (PendingLink existing : pending.values()) {
                if (existing.identity().xuid().equals(identity.xuid()) && !existing.expired(now)) {
                    PendingLink refreshed = new PendingLink(existing.bindingCode(), identity,
                            existing.createdAt(), existing.expiresAt());
                    pending.replace(existing.bindingCode(), existing, refreshed);
                    return refreshed;
                }
            }
        }

        PendingLink created;
        do {
            String code = tokens.crockfordBase32(codeLength);
            created = new PendingLink(code, identity, now, now.plusSeconds(expireSeconds));
        } while (pending.putIfAbsent(created.bindingCode(), created) != null);
        return created;
    }

    public Optional<PendingLink> find(String rawCode, Instant now) {
        String code = normalize(rawCode);
        if (code == null) {
            return Optional.empty();
        }
        PendingLink value = pending.get(code);
        if (value == null || value.expired(now)) {
            if (value != null) {
                pending.remove(code, value);
            }
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public boolean remove(String rawCode) {
        String code = normalize(rawCode);
        return code != null && pending.remove(code) != null;
    }

    public void cleanup(Instant now) {
        pending.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }

    public int size() {
        return pending.size();
    }

    public void clear() {
        pending.clear();
    }

    /**
     * Canonicalises a typed code. Codes are Crockford Base32, so the letters {@code I}, {@code L}
     * and {@code O} never occur in a real code and can be folded onto the digit the player most
     * likely meant; grouping separators are dropped for the same reason. This only widens what is
     * accepted as input — it can never make one player's code collide with another's.
     */
    public static String normalize(String rawCode) {
        if (rawCode == null) {
            return null;
        }
        String upper = rawCode.trim().toUpperCase(Locale.ROOT);
        StringBuilder canonical = new StringBuilder(upper.length());
        for (int index = 0; index < upper.length(); index++) {
            char character = upper.charAt(index);
            switch (character) {
                case '-', '_', ' ', '\t' -> {
                }
                case 'O' -> canonical.append('0');
                case 'I', 'L' -> canonical.append('1');
                default -> canonical.append(character);
            }
        }
        String code = canonical.toString();
        if (code.length() < 8 || code.length() > 32 || !code.matches(CODE_PATTERN)) {
            return null;
        }
        return code;
    }
}
