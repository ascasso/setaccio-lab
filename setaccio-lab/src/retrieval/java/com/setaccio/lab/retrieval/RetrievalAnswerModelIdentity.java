package com.setaccio.lab.retrieval;

/** Immutable identity for the local answer model bound to one R5 run. */
public record RetrievalAnswerModelIdentity(
        String providerId,
        String requestedModel,
        String effectiveModel,
        String digest
) {

    public RetrievalAnswerModelIdentity {
        providerId = requireText(providerId, "providerId");
        requestedModel = requireText(requestedModel, "requestedModel");
        effectiveModel = requireText(effectiveModel, "effectiveModel");
        digest = requireText(digest, "digest");
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("digest must be a full lowercase SHA-256 digest");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(name + " must be a non-blank trimmed value");
        }
        return value;
    }
}
