package com.setaccio.lab.toolcompat;

import java.util.List;

/** Cohort-wide generation settings shared by every selected installed artifact. */
record ToolCompatibilityCohortRunSettings(
        int repetitions,
        double temperature,
        List<Integer> requestedSeeds,
        int maxOutputTokensPerProviderTurn,
        long rowTimeoutMillis,
        int logicalRowAttempts
) {

    ToolCompatibilityCohortRunSettings {
        if (repetitions != ToolCompatibilityProtocol.REPETITIONS
                || Double.compare(temperature, ToolCompatibilityProtocol.TEMPERATURE) != 0
                || maxOutputTokensPerProviderTurn
                        != ToolCompatibilityProtocol.MAX_OUTPUT_TOKENS_PER_PROVIDER_TURN
                || rowTimeoutMillis != ToolCompatibilityProtocol.ROW_TIMEOUT.toMillis()
                || logicalRowAttempts != ToolCompatibilityProtocol.LOGICAL_ROW_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "cohort run settings must preserve the locked tool compatibility protocol");
        }
        requestedSeeds = List.copyOf(requestedSeeds == null ? List.of() : requestedSeeds);
        if (!ToolCompatibilityProtocol.SEEDS.equals(requestedSeeds)) {
            throw new IllegalArgumentException(
                    "cohort requested seeds must equal the locked [42, 43] schedule");
        }
    }

    static ToolCompatibilityCohortRunSettings locked() {
        return new ToolCompatibilityCohortRunSettings(
                ToolCompatibilityProtocol.REPETITIONS,
                ToolCompatibilityProtocol.TEMPERATURE,
                ToolCompatibilityProtocol.SEEDS,
                ToolCompatibilityProtocol.MAX_OUTPUT_TOKENS_PER_PROVIDER_TURN,
                ToolCompatibilityProtocol.ROW_TIMEOUT.toMillis(),
                ToolCompatibilityProtocol.LOGICAL_ROW_ATTEMPTS);
    }
}
