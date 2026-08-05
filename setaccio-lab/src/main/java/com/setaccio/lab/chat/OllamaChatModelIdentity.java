package com.setaccio.lab.chat;

public record OllamaChatModelIdentity(
        String providerId,
        String requestedModel,
        String effectiveModel,
        String digest
) implements ChatProviderModelIdentity {

    public static final String OLLAMA_PROVIDER_ID = "ollama";

    public OllamaChatModelIdentity {
        providerId = requireText(providerId, "providerId");
        if (!OLLAMA_PROVIDER_ID.equals(providerId)) {
            throw new IllegalArgumentException("providerId must be ollama");
        }
        requestedModel = requireText(requestedModel, "requestedModel");
        effectiveModel = requireText(effectiveModel, "effectiveModel");
        digest = requireText(digest, "digest");
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("digest must be a full lowercase SHA-256 digest");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not have surrounding whitespace");
        }
        return value;
    }
}
