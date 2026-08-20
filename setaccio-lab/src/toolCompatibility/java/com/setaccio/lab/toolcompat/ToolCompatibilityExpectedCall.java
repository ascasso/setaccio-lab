package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.JsonNode;

record ToolCompatibilityExpectedCall(String toolName, JsonNode arguments) {

    ToolCompatibilityExpectedCall {
        if (toolName == null || toolName.isBlank() || !toolName.equals(toolName.strip())) {
            throw new IllegalArgumentException("toolName must be nonblank and trimmed");
        }
        if (arguments == null || !arguments.isObject()) {
            throw new IllegalArgumentException("arguments must be a JSON object");
        }
        arguments = arguments.deepCopy();
    }

    @Override
    public JsonNode arguments() {
        return arguments.deepCopy();
    }
}
