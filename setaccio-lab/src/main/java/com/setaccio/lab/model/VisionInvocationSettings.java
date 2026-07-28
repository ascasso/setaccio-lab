package com.setaccio.lab.model;

public record VisionInvocationSettings(
        String model,
        Double temperature,
        Integer seed,
        Integer maxTokens
) {

    public VisionInvocationSettings {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        model = model.trim();
        if (temperature != null
                && (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (seed != null && seed < 0) {
            throw new IllegalArgumentException("seed must not be negative");
        }
        if (maxTokens != null && (maxTokens < 1 || maxTokens > 32768)) {
            throw new IllegalArgumentException("maxTokens must be between 1 and 32768");
        }
    }

    public static VisionInvocationSettings modelDefaults(String model) {
        return new VisionInvocationSettings(model, null, null, null);
    }
}
