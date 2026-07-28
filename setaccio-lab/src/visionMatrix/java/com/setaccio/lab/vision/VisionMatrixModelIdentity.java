package com.setaccio.lab.vision;

public record VisionMatrixModelIdentity(
        String requestedModel,
        String resolvedModel,
        String digest
) {

    public VisionMatrixModelIdentity {
        requireModelTag(requestedModel, "requestedModel");
        requireModelTag(resolvedModel, "resolvedModel");
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("digest must be a full lowercase Ollama digest");
        }
    }

    private static void requireModelTag(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")) {
            throw new IllegalArgumentException(field + " must be a safe Ollama model tag");
        }
    }
}
