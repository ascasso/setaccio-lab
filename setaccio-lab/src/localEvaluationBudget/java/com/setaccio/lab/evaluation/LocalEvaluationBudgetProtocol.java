package com.setaccio.lab.evaluation;

import java.time.Duration;
import java.util.List;

/** The pre-registered F1 protocol; the two arms differ only in max output tokens. */
final class LocalEvaluationBudgetProtocol {

    static final int VERSION = 1;
    static final Duration TIMEOUT = Duration.ofMinutes(2);
    static final List<Integer> MAX_TOKENS = List.of(64, 256);
    static final int ROW_COUNT = 12;
    static final double TEMPERATURE = 0.0;
    static final List<Integer> SEEDS = List.of(42, 43);
    static final int REPETITIONS = 2;
    static final int MAX_ATTEMPTS = 1;

    private LocalEvaluationBudgetProtocol() {}

    static LocalEvaluationRunSettings settings(String requestedModel, int maxTokens) {
        requireArm(maxTokens);
        LocalEvaluationRunSettings settings = LocalEvaluationProtocol.settings(
                requestedModel,
                maxTokens,
                TIMEOUT);
        requireFixedSettings(settings, maxTokens);
        return settings;
    }

    static void requireArm(int maxTokens) {
        if (!MAX_TOKENS.contains(maxTokens)) {
            throw new LocalEvaluationBudgetProtocolIntegrityException(
                    "F1 maximum output tokens must be exactly 64 or 256");
        }
    }

    static void requireFixedSettings(LocalEvaluationRunSettings settings, int expectedMaxTokens) {
        if (settings == null) {
            throw new LocalEvaluationBudgetProtocolIntegrityException(
                    "F1 run settings must not be null");
        }
        requireArm(expectedMaxTokens);
        LocalEvaluationRunSettings expected = LocalEvaluationProtocol.settings(
                settings.requestedModel(),
                expectedMaxTokens,
                TIMEOUT);
        if (!expected.equals(settings)) {
            throw new LocalEvaluationBudgetProtocolIntegrityException(
                    "F1 run settings drifted from the locked two-arm protocol");
        }
    }

    static void requirePairSettings(
            LocalEvaluationRunSettings budget64,
            LocalEvaluationRunSettings budget256
    ) {
        requireFixedSettings(budget64, 64);
        requireFixedSettings(budget256, 256);
        if (!budget64.requestedModel().equals(budget256.requestedModel())
                || budget64.repetitions() != budget256.repetitions()
                || budget64.temperature() != budget256.temperature()
                || !budget64.seeds().equals(budget256.seeds())
                || budget64.timeoutMillis() != budget256.timeoutMillis()
                || budget64.maxAttempts() != budget256.maxAttempts()) {
            throw new LocalEvaluationBudgetProtocolIntegrityException(
                    "F1 arms may differ only in maximum output tokens");
        }
    }
}
