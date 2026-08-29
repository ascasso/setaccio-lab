package com.setaccio.lab.retrieval;

import com.setaccio.lab.chat.ChatGenerationSettings;
import java.time.Duration;

/** Complete material settings for one local R6 relevance-evaluator run. */
public record RetrievalRelevancyRunSettings(
        String provider,
        String endpointCategory,
        double temperature,
        int seed,
        int maxOutputTokens,
        int requestTimeoutMillis,
        int maxAttempts,
        String pullModelStrategy
) {

    public RetrievalRelevancyRunSettings {
        requireText(provider, "provider");
        requireText(endpointCategory, "endpointCategory");
        requireText(pullModelStrategy, "pullModelStrategy");
        if (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (seed < 0 || maxOutputTokens < 1 || requestTimeoutMillis < 1 || maxAttempts != 1) {
            throw new IllegalArgumentException("R6 evaluator settings require non-negative seed and exactly one positive attempt");
        }
        if (!"never".equals(pullModelStrategy)) {
            throw new IllegalArgumentException("pullModelStrategy must be never");
        }
    }

    ChatGenerationSettings chatSettings() {
        return new ChatGenerationSettings(
                temperature, seed, maxOutputTokens, Duration.ofMillis(requestTimeoutMillis), maxAttempts);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be a non-blank trimmed value");
        }
    }
}
