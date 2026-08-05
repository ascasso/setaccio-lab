package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatModelUnavailableException;
import com.setaccio.lab.chat.OllamaChatModelIdentity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.ollama.api.OllamaApi;

final class ChatMatrixModelInventory {

    private ChatMatrixModelInventory() {}

    static OllamaChatModelIdentity requireInstalled(
            OllamaApi.ListModelResponse response,
            String requestedModel
    ) {
        String normalizedRequested = ChatMatrixProtocol.normalizeModelTag(requestedModel);
        Map<String, OllamaApi.Model> installed = new LinkedHashMap<>();
        if (response != null && response.models() != null) {
            for (OllamaApi.Model model : response.models()) {
                if (model == null || model.name() == null || model.name().isBlank()) {
                    continue;
                }
                String normalized = ChatMatrixProtocol.normalizeModelTag(model.name());
                if (installed.putIfAbsent(normalized, model) != null) {
                    throw new IllegalArgumentException(
                            "Installed Ollama model inventory contains duplicate normalized tag: " + normalized);
                }
            }
        }
        OllamaApi.Model resolved = installed.get(normalizedRequested);
        if (resolved == null) {
            throw new ChatModelUnavailableException(
                    "Requested Ollama chat model is not installed: " + normalizedRequested);
        }
        try {
            return new OllamaChatModelIdentity(
                    OllamaChatModelIdentity.OLLAMA_PROVIDER_ID,
                    requestedModel,
                    normalizedRequested,
                    resolved.digest());
        } catch (IllegalArgumentException exception) {
            throw new ChatModelUnavailableException(
                    "Installed Ollama chat model has no complete immutable digest: " + normalizedRequested,
                    exception);
        }
    }
}
