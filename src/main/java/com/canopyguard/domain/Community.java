package com.canopyguard.domain;

public record Community(String id, String name, int population, String evacuationStatus,
                        boolean accessibilitySupport, double longitude, double latitude) {
    public Community {
        if (id == null || id.isBlank() || name == null || name.isBlank() || population < 0)
            throw new IllegalArgumentException("Invalid community record");
    }
}
