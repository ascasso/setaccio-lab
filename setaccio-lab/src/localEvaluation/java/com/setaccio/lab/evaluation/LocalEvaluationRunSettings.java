package com.setaccio.lab.evaluation;

import java.time.Duration;
import java.util.List;

public record LocalEvaluationRunSettings(
        String requestedModel,
        int repetitions,
        double temperature,
        List<Integer> seeds,
        int maxTokens,
        long timeoutMillis,
        int maxAttempts
) {

    public LocalEvaluationRunSettings {
        if (requestedModel == null
                || !requestedModel.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")) {
            throw new IllegalArgumentException("requestedModel must be a safe Ollama model tag");
        }
        if (repetitions < 1) {
            throw new IllegalArgumentException("repetitions must be positive");
        }
        if (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        seeds = seeds == null ? List.of() : List.copyOf(seeds);
        if (seeds.size() != repetitions
                || seeds.stream().anyMatch(seed -> seed == null || seed < 0)) {
            throw new IllegalArgumentException("seeds must contain one non-negative seed per repetition");
        }
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (timeoutMillis < 1) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        if (maxAttempts != 1) {
            throw new IllegalArgumentException("maxAttempts must be exactly 1");
        }
    }

    int seedFor(int repetition) {
        if (repetition < 1 || repetition > repetitions) {
            throw new IllegalArgumentException("repetition is outside the configured range");
        }
        return seeds.get(repetition - 1);
    }

    LocalFactCheckJudgeSettings judgeSettingsFor(int repetition) {
        return new LocalFactCheckJudgeSettings(
                requestedModel,
                temperature,
                seedFor(repetition),
                maxTokens,
                Duration.ofMillis(timeoutMillis),
                maxAttempts);
    }
}
