package com.setaccio.lab.retrieval;

import com.setaccio.lab.chat.ChatGenerationSettings;
import java.time.Duration;

/** Complete material settings for one local R5 answer-generation run. */
public record RetrievalAnswerRunSettings(
        String provider,
        String endpointCategory,
        double temperature,
        int seed,
        int maxOutputTokens,
        int requestTimeoutMillis,
        int maxAttempts,
        String pullModelStrategy
) {

    public RetrievalAnswerRunSettings {
        requireText(provider, "provider");
        requireText(endpointCategory, "endpointCategory");
        requireText(pullModelStrategy, "pullModelStrategy");
        if (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (seed < 0) {
            throw new IllegalArgumentException("seed must not be negative");
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        if (requestTimeoutMillis < 1) {
            throw new IllegalArgumentException("requestTimeoutMillis must be positive");
        }
        if (maxAttempts != 1) {
            throw new IllegalArgumentException("maxAttempts must be exactly one");
        }
        if (!"never".equals(pullModelStrategy)) {
            throw new IllegalArgumentException("pullModelStrategy must be never");
        }
    }

    ChatGenerationSettings chatSettings() {
        return new ChatGenerationSettings(
                temperature,
                seed,
                maxOutputTokens,
                Duration.ofMillis(requestTimeoutMillis),
                maxAttempts);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(name + " must be a non-blank trimmed value");
        }
    }
}
