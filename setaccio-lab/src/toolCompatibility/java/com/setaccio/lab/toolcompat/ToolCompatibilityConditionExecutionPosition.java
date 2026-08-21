package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

enum ToolCompatibilityConditionExecutionPosition {

    FIRST("first"),
    SECOND("second");

    private final String wireValue;

    ToolCompatibilityConditionExecutionPosition(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    String wireValue() {
        return wireValue;
    }

    @JsonCreator
    static ToolCompatibilityConditionExecutionPosition fromWireValue(String value) {
        for (ToolCompatibilityConditionExecutionPosition position : values()) {
            if (position.wireValue.equals(value)) {
                return position;
            }
        }
        throw new IllegalArgumentException("Unknown condition execution position: " + value);
    }
}
