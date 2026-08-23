package com.setaccio.lab.toolcompat;

/** Records whether optional cohort identity metadata was exposed by the local runtime. */
record ToolCompatibilityMetadataField(
        Availability availability,
        String value
) {

    ToolCompatibilityMetadataField {
        if (availability == null) {
            throw new IllegalArgumentException("metadata availability is required");
        }
        if (availability == Availability.AVAILABLE) {
            if (value == null || value.isBlank() || !value.equals(value.strip())) {
                throw new IllegalArgumentException(
                        "available metadata must have one nonblank trimmed value");
            }
        } else if (value != null) {
            throw new IllegalArgumentException("unavailable metadata must not invent a value");
        }
    }

    static ToolCompatibilityMetadataField available(String value) {
        return new ToolCompatibilityMetadataField(Availability.AVAILABLE, value);
    }

    static ToolCompatibilityMetadataField unavailable() {
        return new ToolCompatibilityMetadataField(Availability.UNAVAILABLE, null);
    }

    enum Availability {
        AVAILABLE,
        UNAVAILABLE
    }
}
