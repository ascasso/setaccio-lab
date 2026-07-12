package com.setaccio.lab.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ToolBenchmarkExpectation(
        List<String> requiredExecutedTools,
        List<String> forbiddenExecutedTools,
        List<String> requiredOutputTerms,
        List<String> requiredToolResponseTerms
) {
    public ToolBenchmarkExpectation {
        requiredExecutedTools = normalized(requiredExecutedTools);
        forbiddenExecutedTools = normalized(forbiddenExecutedTools);
        requiredOutputTerms = normalized(requiredOutputTerms);
        requiredToolResponseTerms = normalized(requiredToolResponseTerms);

        Set<String> overlap = new LinkedHashSet<>(requiredExecutedTools);
        overlap.retainAll(forbiddenExecutedTools);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException(
                    "Tools cannot be both required and forbidden: " + String.join(", ", overlap));
        }
    }

    public static ToolBenchmarkExpectation none() {
        return new ToolBenchmarkExpectation(List.of(), List.of(), List.of(), List.of());
    }

    public boolean hasChecks() {
        return !requiredExecutedTools.isEmpty()
                || !forbiddenExecutedTools.isEmpty()
                || !requiredOutputTerms.isEmpty()
                || !requiredToolResponseTerms.isEmpty();
    }

    private static List<String> normalized(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}
