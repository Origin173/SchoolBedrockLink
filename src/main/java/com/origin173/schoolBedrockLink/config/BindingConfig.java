package com.origin173.schoolBedrockLink.config;

public record BindingConfig(int codeLength, int codeExpireSeconds, boolean reuseExistingCode,
                            boolean autoRepairMissingFloodgateLink) {
}
