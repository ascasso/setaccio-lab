package com.setaccio.lab.evaluation;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.ollama.api.OllamaApi;

final class LocalEvaluationModelInventory {

    private LocalEvaluationModelInventory() {}

    static LocalEvaluationModelIdentity requireInstalled(
            OllamaApi.ListModelResponse response,
            String requestedModel
    ) {
        String normalizedRequested = LocalEvaluationProtocol.normalizeModelTag(requestedModel);
        Map<String, OllamaApi.Model> installed = new LinkedHashMap<>();
        if (response != null && response.models() != null) {
            for (OllamaApi.Model model : response.models()) {
                if (model == null || model.name() == null || model.name().isBlank()) {
                    continue;
                }
                String normalized = LocalEvaluationProtocol.normalizeModelTag(model.name());
                if (installed.putIfAbsent(normalized, model) != null) {
                    throw new IllegalArgumentException(
                            "Installed Ollama model inventory contains duplicate normalized tag: " + normalized);
                }
            }
        }
        OllamaApi.Model resolved = installed.get(normalizedRequested);
        if (resolved == null) {
            throw new LocalFactCheckJudgeModelUnavailableException(
                    "Requested Ollama judge model is not installed: " + normalizedRequested);
        }
        try {
            return new LocalEvaluationModelIdentity(
                    requestedModel,
                    normalizedRequested,
                    resolved.digest());
        } catch (IllegalArgumentException exception) {
            throw new LocalFactCheckJudgeModelUnavailableException(
                    "Installed Ollama judge model has no complete immutable digest: " + normalizedRequested,
                    exception);
        }
    }
}
