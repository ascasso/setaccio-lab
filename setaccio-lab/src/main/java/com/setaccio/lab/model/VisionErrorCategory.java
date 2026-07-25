package com.setaccio.lab.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum VisionErrorCategory {
    INVALID_INPUT("invalid_input"),
    MODEL_UNAVAILABLE("model_unavailable"),
    EMPTY_RESPONSE("empty_response"),
    PROVIDER_FAILURE("provider_failure");

    private final String jsonValue;

    VisionErrorCategory(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
