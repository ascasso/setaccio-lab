package com.setaccio.lab.chat;

import java.time.Duration;
import java.util.Objects;

public record ChatGenerationSettings(
        double temperature,
        Integer seed,
        int maxOutputTokens,
        Duration requestTimeout,
        int maxAttempts
) {
    public ChatGenerationSettings {
        if (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (seed != null && seed < 0) {
            throw new IllegalArgumentException("seed must not be negative");
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        if (maxAttempts != 1) {
            throw new IllegalArgumentException("maxAttempts must be exactly 1 for controlled chat invocation");
        }
    }
}
