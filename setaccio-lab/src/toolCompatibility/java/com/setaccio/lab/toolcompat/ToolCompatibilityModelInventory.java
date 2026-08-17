package com.setaccio.lab.toolcompat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.ollama.api.OllamaApi;

final class ToolCompatibilityModelInventory {

    private ToolCompatibilityModelInventory() {}

    static ToolCompatibilityModelIdentity requireInstalled(
            OllamaApi.ListModelResponse response,
            String requestedModel
    ) {
        if (!ToolCompatibilityProtocol.INITIAL_MODEL.equals(requestedModel)) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Requested model must equal the locked tool compatibility model");
        }
        String normalizedRequested = normalizeModelTag(requestedModel);
        Map<String, OllamaApi.Model> installed = new LinkedHashMap<>();
        if (response != null && response.models() != null) {
            for (OllamaApi.Model model : response.models()) {
                if (model == null || model.name() == null || model.name().isBlank()) {
                    continue;
                }
                String normalized = normalizeModelTag(model.name());
                if (installed.putIfAbsent(normalized, model) != null) {
                    throw new ToolCompatibilityProtocolIntegrityException(
                            "Installed Ollama model inventory contains duplicate normalized tag: " + normalized);
                }
            }
        }
        OllamaApi.Model resolved = installed.get(normalizedRequested);
        if (resolved == null) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Requested Ollama tool compatibility model is not installed: " + normalizedRequested);
        }
        try {
            return new ToolCompatibilityModelIdentity(
                    requestedModel,
                    normalizedRequested,
                    resolved.digest());
        } catch (IllegalArgumentException exception) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Installed Ollama tool compatibility model has no complete immutable digest: "
                            + normalizedRequested,
                    exception);
        }
    }

    private static String normalizeModelTag(String model) {
        String normalized = model.strip();
        return normalized.contains(":") ? normalized : normalized + ":latest";
    }
}
