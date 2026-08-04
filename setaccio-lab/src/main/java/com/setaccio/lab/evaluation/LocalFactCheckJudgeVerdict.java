package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.annotation.JsonValue;

public enum LocalFactCheckJudgeVerdict {
    SUPPORTED("supported"),
    UNSUPPORTED("unsupported");

    private final String jsonValue;

    LocalFactCheckJudgeVerdict(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    static LocalFactCheckJudgeVerdict normalize(String rawResponse) {
        if (rawResponse == null) {
            return null;
        }
        return switch (rawResponse.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "yes" -> SUPPORTED;
            case "no" -> UNSUPPORTED;
            default -> null;
        };
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
