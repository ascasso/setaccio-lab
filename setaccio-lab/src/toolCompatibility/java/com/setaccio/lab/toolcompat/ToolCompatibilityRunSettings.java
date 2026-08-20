package com.setaccio.lab.toolcompat;

import java.util.List;

record ToolCompatibilityRunSettings(
        String requestedModel,
        int repetitions,
        double temperature,
        List<Integer> seeds,
        int maxOutputTokensPerProviderTurn,
        long rowTimeoutMillis,
        int logicalRowAttempts
) {

    ToolCompatibilityRunSettings {
        if (!ToolCompatibilityProtocol.INITIAL_MODEL.equals(requestedModel)) {
            throw new IllegalArgumentException("requestedModel must equal the locked initial model");
        }
        if (repetitions != ToolCompatibilityProtocol.REPETITIONS) {
            throw new IllegalArgumentException("repetitions must equal the locked two-repetition protocol");
        }
        if (Double.compare(temperature, ToolCompatibilityProtocol.TEMPERATURE) != 0) {
            throw new IllegalArgumentException("temperature must equal the locked value 0.0");
        }
        seeds = seeds == null ? List.of() : List.copyOf(seeds);
        if (!ToolCompatibilityProtocol.SEEDS.equals(seeds)) {
            throw new IllegalArgumentException("seeds must equal the locked [42, 43] schedule");
        }
        if (maxOutputTokensPerProviderTurn != ToolCompatibilityProtocol.MAX_OUTPUT_TOKENS_PER_PROVIDER_TURN) {
            throw new IllegalArgumentException("maxOutputTokensPerProviderTurn must equal the locked value 512");
        }
        if (rowTimeoutMillis != ToolCompatibilityProtocol.ROW_TIMEOUT.toMillis()) {
            throw new IllegalArgumentException("rowTimeoutMillis must equal the locked PT2M deadline");
        }
        if (logicalRowAttempts != ToolCompatibilityProtocol.LOGICAL_ROW_ATTEMPTS) {
            throw new IllegalArgumentException("logicalRowAttempts must equal one");
        }
    }

    int seedFor(int repetition) {
        if (repetition < 1 || repetition > repetitions) {
            throw new IllegalArgumentException("repetition is outside the locked range");
        }
        return seeds.get(repetition - 1);
    }
}
