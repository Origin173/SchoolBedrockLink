package com.origin173.schoolBedrockLink.oauth;

/** Server-side profile selection: the browser supplies only an index, never a UUID. */
public final class ProfileSelection {

    private ProfileSelection() {
    }

    public static MinecraftProfile select(ConfirmationSession session, int profileIndex) {
        if (profileIndex < 0 || profileIndex >= session.profiles().size()) {
            throw new IllegalArgumentException("profile index is outside the OAuth result");
        }
        return session.profiles().get(profileIndex);
    }
}
