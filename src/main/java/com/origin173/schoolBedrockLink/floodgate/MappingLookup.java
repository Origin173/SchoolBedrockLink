package com.origin173.schoolBedrockLink.floodgate;

public record MappingLookup(boolean available, FloodgateMapping mapping) {

    public static MappingLookup unavailable() {
        return new MappingLookup(false, null);
    }

    public static MappingLookup available(FloodgateMapping mapping) {
        return new MappingLookup(true, mapping);
    }
}
