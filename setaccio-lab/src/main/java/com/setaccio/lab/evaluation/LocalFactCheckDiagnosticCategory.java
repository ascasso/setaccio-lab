package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.annotation.JsonValue;

public enum LocalFactCheckDiagnosticCategory {
    NONE("none"),
    JUDGE_MODEL_UNAVAILABLE("judge_model_unavailable"),
    TIMEOUT("timeout"),
    PROVIDER_FAILURE("provider_failure"),
    EMPTY_RESPONSE("empty_response"),
    MALFORMED_VERDICT("malformed_verdict"),
    EXPECTATION_MISMATCH("expectation_mismatch");

    private final String jsonValue;

    LocalFactCheckDiagnosticCategory(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
