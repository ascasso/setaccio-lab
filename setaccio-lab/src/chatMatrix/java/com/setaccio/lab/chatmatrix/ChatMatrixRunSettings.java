package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatGenerationSettings;
import java.time.Duration;
import java.util.List;

record ChatMatrixRunSettings(
        String requestedModel,
        int repetitions,
        double temperature,
        List<Integer> seeds,
        int maxOutputTokens,
        long timeoutMillis,
        int maxAttempts
) {
    ChatMatrixRunSettings {
        if (requestedModel == null || !requestedModel.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")) {
            throw new IllegalArgumentException("requestedModel must be a safe Ollama model tag");
        }
        if (repetitions < 1) {
            throw new IllegalArgumentException("repetitions must be positive");
        }
        if (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        seeds = seeds == null ? List.of() : List.copyOf(seeds);
        if (seeds.size() != repetitions || seeds.stream().anyMatch(seed -> seed == null || seed < 0)) {
            throw new IllegalArgumentException("seeds must contain one non-negative seed per repetition");
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
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

    ChatGenerationSettings generationSettingsFor(int repetition) {
        return new ChatGenerationSettings(
                temperature,
                seedFor(repetition),
                maxOutputTokens,
                Duration.ofMillis(timeoutMillis),
                maxAttempts);
    }
}
