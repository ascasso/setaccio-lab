package com.setaccio.lab.vision;

import java.util.LinkedHashSet;
import java.util.List;

public record VisionMatrixRunSettings(
        List<String> models,
        int repetitions,
        double temperature,
        int baseSeed,
        Integer maxTokens
) {

    public VisionMatrixRunSettings {
        models = models == null ? List.of() : models.stream().map(String::trim).toList();
        if (models.isEmpty() || models.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("models must contain non-blank values");
        }
        if (models.stream().anyMatch(model -> !model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*"))) {
            throw new IllegalArgumentException("models must contain safe Ollama model tags");
        }
        if (new LinkedHashSet<>(models).size() != models.size()) {
            throw new IllegalArgumentException("models must not contain duplicates");
        }
        if (repetitions < 1) {
            throw new IllegalArgumentException("repetitions must be positive");
        }
        if (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (baseSeed < 0) {
            throw new IllegalArgumentException("baseSeed must not be negative");
        }
        if (maxTokens != null && (maxTokens < 1 || maxTokens > 32768)) {
            throw new IllegalArgumentException("maxTokens must be between 1 and 32768");
        }
    }

    int seedFor(int repetition) {
        if (repetition < 1 || repetition > repetitions) {
            throw new IllegalArgumentException("repetition is outside the configured range");
        }
        return Math.addExact(baseSeed, repetition - 1);
    }
}
