package com.setaccio.lab.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Locale;

public enum ToolBenchmarkComparisonOrder {
    ALTERNATE("alternate"),
    STANDARD_FIRST("standard_first"),
    TOOL_SEARCH_FIRST("tool_search_first");

    private final String jsonValue;

    ToolBenchmarkComparisonOrder(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static ToolBenchmarkComparisonOrder fromJson(String value) {
        if (value == null || value.isBlank()) {
            return ALTERNATE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (ToolBenchmarkComparisonOrder order : values()) {
            if (order.jsonValue.equals(normalized) || order.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return order;
            }
        }
        throw new IllegalArgumentException("Unsupported comparison order: " + value);
    }

    public List<AdvisorMode> modesFor(int repetition) {
        return switch (this) {
            case STANDARD_FIRST -> List.of(AdvisorMode.STANDARD, AdvisorMode.TOOL_SEARCH);
            case TOOL_SEARCH_FIRST -> List.of(AdvisorMode.TOOL_SEARCH, AdvisorMode.STANDARD);
            case ALTERNATE -> repetition % 2 == 1
                    ? List.of(AdvisorMode.STANDARD, AdvisorMode.TOOL_SEARCH)
                    : List.of(AdvisorMode.TOOL_SEARCH, AdvisorMode.STANDARD);
        };
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
