package com.setaccio.lab.evaluation;

import java.time.Duration;
import java.util.List;

/** Locked five-arm follow-up to the completed Phase 4 64/256-token comparison. */
final class LocalEvaluationBreakpointProtocol {

    static final int VERSION = 1;
    static final Duration TIMEOUT = Duration.ofMinutes(2);
    static final List<Integer> MAX_TOKENS = List.of(64, 96, 128, 192, 256);
    static final int ROW_COUNT = 12;
    static final double TEMPERATURE = 0.0;
    static final List<Integer> SEEDS = List.of(42, 43);
    static final int REPETITIONS = 2;
    static final int MAX_ATTEMPTS = 1;

    private LocalEvaluationBreakpointProtocol() {}

    static LocalEvaluationRunSettings settings(String requestedModel, int maxTokens) {
        requireArm(maxTokens);
        LocalEvaluationRunSettings settings = LocalEvaluationProtocol.settings(requestedModel, maxTokens, TIMEOUT);
        requireFixedSettings(settings, maxTokens);
        return settings;
    }

    static void requireArm(int maxTokens) {
        if (!MAX_TOKENS.contains(maxTokens)) {
            throw new LocalEvaluationBudgetProtocolIntegrityException(
                    "Breakpoint maximum output tokens must be exactly one of 64, 96, 128, 192, or 256");
        }
    }

    static void requireFixedSettings(LocalEvaluationRunSettings settings, int expectedMaxTokens) {
        if (settings == null) {
            throw new LocalEvaluationBudgetProtocolIntegrityException("Breakpoint run settings must not be null");
        }
        requireArm(expectedMaxTokens);
        LocalEvaluationRunSettings expected = LocalEvaluationProtocol.settings(
                settings.requestedModel(), expectedMaxTokens, TIMEOUT);
        if (!expected.equals(settings)) {
            throw new LocalEvaluationBudgetProtocolIntegrityException(
                    "Breakpoint run settings drifted from the locked five-arm protocol");
        }
    }

    static void requireStudySettings(List<LocalEvaluationRunSettings> settings) {
        if (settings == null || settings.size() != MAX_TOKENS.size()) {
            throw new LocalEvaluationBudgetProtocolIntegrityException("Breakpoint study requires five token arms");
        }
        String requestedModel = null;
        for (int index = 0; index < MAX_TOKENS.size(); index++) {
            LocalEvaluationRunSettings arm = settings.get(index);
            requireFixedSettings(arm, MAX_TOKENS.get(index));
            if (requestedModel == null) {
                requestedModel = arm.requestedModel();
            } else if (!requestedModel.equals(arm.requestedModel())) {
                throw new LocalEvaluationBudgetProtocolIntegrityException(
                        "Breakpoint arms may differ only in maximum output tokens");
            }
        }
    }
}
