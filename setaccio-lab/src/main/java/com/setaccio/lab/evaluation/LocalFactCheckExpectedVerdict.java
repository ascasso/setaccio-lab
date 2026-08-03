package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum LocalFactCheckExpectedVerdict {
    SUPPORTED,
    UNSUPPORTED;

    @JsonCreator
    public static LocalFactCheckExpectedVerdict fromJson(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("expectedVerdict must not be blank");
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported expectedVerdict: " + value, exception);
        }
    }

    @JsonValue
    public String jsonValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
