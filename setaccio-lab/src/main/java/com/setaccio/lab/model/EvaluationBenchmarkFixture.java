package com.setaccio.lab.model;

import java.util.List;
import java.util.Objects;

public record EvaluationBenchmarkFixture(
        String id,
        String userText,
        String contextText,
        String responseText,
        List<String> requiredTerms
) {
    public EvaluationBenchmarkFixture {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userText, "userText must not be null");
        Objects.requireNonNull(responseText, "responseText must not be null");
        requiredTerms = List.copyOf(requiredTerms);
    }
}
