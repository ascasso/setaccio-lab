package com.setaccio.lab.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum AdvisorMode {
    STANDARD("standard"),
    TOOL_SEARCH("tool_search"),
    COMPARE("compare");

    private final String jsonValue;

    AdvisorMode(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static AdvisorMode fromJson(String value) {
        if (value == null || value.isBlank()) {
            return STANDARD;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (AdvisorMode mode : values()) {
            if (mode.jsonValue.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported advisor mode: " + value);
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
