package com.origin173.schoolBedrockLink.floodgate;

public record FloodgateProbe(Kind kind, BedrockIdentity identity) {

    public enum Kind {
        JAVA,
        BEDROCK,
        BEDROCK_IDENTITY_UNAVAILABLE,
        API_UNAVAILABLE
    }

    public boolean isBedrock() {
        return kind == Kind.BEDROCK;
    }
}
