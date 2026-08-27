package com.setaccio.lab.retrieval;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.ollama.api.OllamaApi;

/** Resolves one already-installed Ollama model to an immutable digest before R4 generation. */
final class RetrievalEmbeddingModelInventory {

    private RetrievalEmbeddingModelInventory() {}

    static RetrievalEmbeddingModelIdentity requireInstalled(
            OllamaApi.ListModelResponse response,
            String requestedModel
    ) {
        if (requestedModel == null || requestedModel.isBlank() || !requestedModel.equals(requestedModel.strip())) {
            throw new IllegalArgumentException("embedding model must be an explicit non-blank tag");
        }
        Map<String, OllamaApi.Model> installed = new LinkedHashMap<>();
        if (response != null && response.models() != null) {
            for (OllamaApi.Model model : response.models()) {
                if (model == null || model.name() == null || model.name().isBlank()) {
                    continue;
                }
                if (installed.putIfAbsent(model.name(), model) != null) {
                    throw new IllegalArgumentException(
                            "Installed Ollama inventory contains duplicate model tag: " + model.name());
                }
            }
        }
        OllamaApi.Model model = installed.get(requestedModel);
        if (model == null) {
            throw new IllegalArgumentException("Requested Ollama embedding model is not installed: " + requestedModel);
        }
        return new RetrievalEmbeddingModelIdentity(requestedModel, model.name(), model.digest());
    }
}
