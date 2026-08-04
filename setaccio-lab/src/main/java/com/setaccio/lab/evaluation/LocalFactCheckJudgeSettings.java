package com.setaccio.lab.evaluation;

import java.time.Duration;
import java.util.Objects;
import org.springframework.ai.ollama.api.OllamaChatOptions;

public record LocalFactCheckJudgeSettings(
        String model,
        double temperature,
        int seed,
        int maxTokens,
        Duration timeout,
        int maxAttempts
) {
    public LocalFactCheckJudgeSettings {
        model = requireText(model, "model");
        if (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (seed < 0) {
            throw new IllegalArgumentException("seed must not be negative");
        }
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxAttempts != 1) {
            throw new IllegalArgumentException("maxAttempts must be exactly 1 for the local fact-check judge");
        }
    }

    public OllamaChatOptions ollamaOptions() {
        return OllamaChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .seed(seed)
                .numPredict(maxTokens)
                .build();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not have surrounding whitespace");
        }
        return value;
    }
}
