package com.setaccio.lab.vision;

import com.setaccio.lab.evidence.EvidenceSuiteRoot;
import com.setaccio.lab.service.VisionPromptDefinition;
import com.setaccio.lab.service.VisionPromptCatalog;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VisionMatrixProtocol {

    static final String SUITE = "vision-matrix";
    static final EvidenceSuiteRoot EVIDENCE_ROOT = EvidenceSuiteRoot.of("vision-matrix");
    static final EvidenceSuiteRoot HUMAN_REVIEW_ROOT = EvidenceSuiteRoot.of("vision-human-review");
    static final String PROVIDER = "ollama";
    static final String HOST = "local";
    static final String EXECUTION_ENGINE = "spring-ai-direct";
    static final String EXECUTION_STRATEGY = "sequential";
    static final String PULL_MODEL_STRATEGY = "never";
    static final int REPETITIONS = 2;
    static final double TEMPERATURE = 0.0;
    static final int BASE_SEED = 42;
    static final String RAW_FILENAME = "vision-matrix-results.json";

    private VisionMatrixProtocol() {}

    static VisionMatrixRunSettings settings(List<String> models, Integer maxTokens) {
        return new VisionMatrixRunSettings(
                models,
                REPETITIONS,
                TEMPERATURE,
                BASE_SEED,
                maxTokens);
    }

    static String normalizeModelTag(String model) {
        String normalized = model.trim();
        return normalized.contains(":") ? normalized : normalized + ":latest";
    }

    static Map<String, Object> manifestSettings(VisionMatrixResult result) {
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        settings.put("models", result.runSettings().models());
        settings.put("modelIdentities", result.modelIdentities());
        settings.put("caseIds", result.inputs().stream().map(VisionMatrixInput::caseId).toList());
        settings.put("inputs", result.inputs());
        settings.put("runSettings", result.runSettings());
        settings.put("executionStrategy", result.executionStrategy());
        settings.put("pullModelStrategy", result.pullModelStrategy());
        settings.put("promptId", result.promptId());
        settings.put("promptVersion", result.promptVersion());
        settings.put("promptSha256", result.promptSha256());
        return Collections.unmodifiableMap(settings);
    }

    static void requirePrompt(VisionPromptDefinition promptDefinition) {
        if (!VisionPromptDefinition.ID.equals(promptDefinition.id())
                || !VisionPromptCatalog.supports(promptDefinition.version())) {
            throw new IllegalStateException("Vision prompt identity drifted from the locked contract");
        }
    }
}
