package com.origin173.schoolBedrockLink.config;

public record MessageConfig(String unlinked, String repaired, String conflict, String notReady) {

    /**
     * Renders the unlinked message. {@code link} is the ready-to-open URL that already carries the
     * code; {@code url} and {@code code} keep the manual path working when the player cannot tap or
     * copy the link (for example on a console), so no existing flow regresses.
     */
    public String unlinked(String url, String link, String code, long minutes) {
        return unlinked
                .replace("{url}", url == null ? "" : url)
                .replace("{link}", link == null ? "" : link)
                .replace("{code}", code == null ? "" : code)
                .replace("{minutes}", Long.toString(minutes));
    }
}
