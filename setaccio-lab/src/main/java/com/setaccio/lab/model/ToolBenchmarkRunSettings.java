package com.setaccio.lab.model;

public record ToolBenchmarkRunSettings(
        int repetitions,
        double temperature,
        int baseSeed,
        Integer maxTokens,
        ToolBenchmarkComparisonOrder comparisonOrder
) {
    public static final int DEFAULT_STANDARD_REPETITIONS = 1;
    public static final int DEFAULT_COMPARISON_REPETITIONS = 2;
    public static final double DEFAULT_TEMPERATURE = 0.0;
    public static final int DEFAULT_BASE_SEED = 42;
    public static final int MAX_REPETITIONS = 20;

    public ToolBenchmarkRunSettings {
        if (repetitions < 1 || repetitions > MAX_REPETITIONS) {
            throw new IllegalArgumentException(
                    "repetitions must be between 1 and " + MAX_REPETITIONS);
        }
        if (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (baseSeed < 0 || baseSeed > Integer.MAX_VALUE - repetitions + 1) {
            throw new IllegalArgumentException("baseSeed must allow one non-negative seed per repetition");
        }
        if (maxTokens != null && (maxTokens < 1 || maxTokens > 32768)) {
            throw new IllegalArgumentException("maxTokens must be between 1 and 32768");
        }
        comparisonOrder = comparisonOrder == null ? ToolBenchmarkComparisonOrder.ALTERNATE : comparisonOrder;
    }

    public static ToolBenchmarkRunSettings resolve(
            Integer repetitions,
            Double temperature,
            Integer baseSeed,
            Integer maxTokens,
            ToolBenchmarkComparisonOrder comparisonOrder,
            boolean comparison) {
        return new ToolBenchmarkRunSettings(
                repetitions == null
                        ? comparison ? DEFAULT_COMPARISON_REPETITIONS : DEFAULT_STANDARD_REPETITIONS
                        : repetitions,
                temperature == null ? DEFAULT_TEMPERATURE : temperature,
                baseSeed == null ? DEFAULT_BASE_SEED : baseSeed,
                maxTokens,
                comparisonOrder
        );
    }

    public static ToolBenchmarkRunSettings standardDefaults() {
        return resolve(null, null, null, null, null, false);
    }

    public static ToolBenchmarkRunSettings comparisonDefaults() {
        return resolve(null, null, null, null, null, true);
    }

    public int seedFor(int repetition) {
        if (repetition < 1 || repetition > repetitions) {
            throw new IllegalArgumentException("repetition is outside the configured range");
        }
        return baseSeed + repetition - 1;
    }
}
